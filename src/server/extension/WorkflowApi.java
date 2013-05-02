package server.extension;

import java.util.Map;
import java.util.List;

import javax.persistence.EntityManager;

import org.dom4j.Document;
import org.dom4j.Node;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import server.*;
import server.dao.DAOFactory;
import server.dao.FolderDAO;
import server.dao.ObjectSystemDataDAO;
import server.dao.ObjectTypeDAO;
import server.dao.RelationDAO;
import server.dao.RelationTypeDAO;
import server.dao.UserDAO;
import server.data.ObjectSystemData;
import server.data.Validator;
import server.exceptions.CinnamonConfigurationException;
import server.exceptions.CinnamonException;
import server.global.Constants;
import server.global.PermissionName;
import server.index.LuceneBridge;
import server.interfaces.CommandRegistry;
import server.interfaces.Repository;
import server.interfaces.Response;
import server.response.XmlResponse;
import utils.ParamParser;
import server.interfaces.Transition;

public class WorkflowApi extends BaseExtension{

	static {
		/*
		 * Required so BaseExtension can set the API-Class.
		 */
		setExtensionClass(WorkflowApi.class);
	}
	
	private Logger log = LoggerFactory.getLogger(this.getClass());
	
	public CommandRegistry registerApi(CommandRegistry cmdReg) {
		return findAndRegisterMethods(cmdReg, this);
	}
	
	public WorkflowApi(){}
	
	public WorkflowApi(Repository repository, EntityManager em){
		this.repository = repository;
		this.em = em;
	}
	
	static DAOFactory daoFactory = DAOFactory.instance(DAOFactory.HIBERNATE);
	
	/**
	 * Create a new Workflow. User must have CREATE_INSTANCE-Permission.
	 * @return
	 * {@code
	 * 	<workflowId>$id of workflow OSD</workflowId>
	 * }
	 * <h2>Parameters in HTTP Request</h2>
	 * <ul>
	 * <li>command=createworkflow</li>
	 * <li>template_id= workflow-template-id</li>
	 * <li>ticket=session ticket</li>
	 * </ul>
	 * @param cmd Map with String,Value parameter pairs (taken from the HTTP Request)
	 */
	@CinnamonMethod(checkTrigger = "true")
	public Response createWorkflow(Map<String,String> cmd){		
	
		String templateId	= cmd.get("template_id");
		
		/*
		 * x fetch the template
		 * x fetch the workflow folder
		 * x fetch the task definition of the start_task (via relation)
		 * x fetch the task folder
		 * x fetch object types
		 * x copy template to workflow folder (= new workflow object)
		 * x copy start_task definition to task folder (= new task object)
		 * x set procstate of start_task to todo
		 * x create relation between workflow and task
		 * x index new workflow
		 * x index new task
		 * x return workflow id
		 */
		
		// fetch the template
		ObjectSystemDataDAO oDao = daoFactory.getObjectSystemDataDAO(em);
		ObjectSystemData workflowTemplate = oDao.get(templateId);
		if(workflowTemplate == null || 
				! workflowTemplate.getType().getName().equals(Constants.OBJTYPE_WORKFLOW_TEMPLATE)){
			throw new CinnamonConfigurationException("error.template.not_found");			
		}
		new Validator(user).validatePermissions(workflowTemplate, PermissionName.CREATE_INSTANCE);

		// fetch template folder
		FolderDAO fDao = daoFactory.getFolderDAO(em);
		Folder workflowFolder = fDao.findByPath(Constants.WORKFLOW_FOLDER_PATH);

		log.debug("Creating new Workflow from template");

		// copy workflow template to workflow:
		ObjectTypeDAO otDao = daoFactory.getObjectTypeDAO(em);
		ObjectType workflowType = otDao.findByName(Constants.OBJTYPE_WORKFLOW);
		ObjectSystemDataDAO osdDao = daoFactory.getObjectSystemDataDAO(em);
	    ObjectSystemData workflow = workflowTemplate.createClone();
	    workflow.setAcl(workflowFolder.getAcl()); // set ACL to target folder's ACL
	    workflow.setParent(workflowFolder);
		workflow.setPredecessor(null);
		workflow.setOwner(getUser());
		workflow.setType(workflowType);
		workflow.setProcstate(Constants.PROCSTATE_WORKFLOW_STARTED);
		osdDao.makePersistent(workflow);

		log.debug("Creating new Start Task from task_definition");
		RelationTypeDAO rtDao = daoFactory.getRelationTypeDAO(em);
		RelationType startTaskRelType = rtDao.findByName(Constants.RELATION_TYPE_WORKFLOW_TO_START_TASK);
		RelationDAO relDao = daoFactory.getRelationDAO(em);


		List<Relation> relations = relDao.findAllByLeftAndType(workflowTemplate, startTaskRelType);
		if(relations.isEmpty()){
			throw new CinnamonException("error.missing.start_task_relation");
		}
		ObjectSystemData startTaskDef = relations.get(0).getRight();
		ObjectSystemData startTask = createTask(startTaskDef, workflow );

		XmlResponse resp = new XmlResponse(res);
		resp.addTextNode("workflowId", String.valueOf(workflow.getId()));
		return resp;
	}

	public ObjectSystemData createTask(ObjectSystemData taskDef, ObjectSystemData workflow){
		// possible optimization: add the fooDAOs as fields to the class.
		FolderDAO fDao = daoFactory.getFolderDAO(em);
		Folder taskFolder = fDao.findByPath(Constants.WORKFLOW_TASK_PATH);
		ObjectTypeDAO otDao = daoFactory.getObjectTypeDAO(em);
		ObjectType taskType = otDao.findByName(Constants.OBJTYPE_TASK);
		ObjectSystemDataDAO osdDao = daoFactory.getObjectSystemDataDAO(em);
		RelationTypeDAO rtDao = daoFactory.getRelationTypeDAO(em);
		RelationDAO relDao = daoFactory.getRelationDAO(em);

		ObjectSystemData task = taskDef.createClone();
	    task.setAcl(taskFolder.getAcl());
	    task.setParent(taskFolder);
		task.setPredecessor(null);
		task.setOwner(getUser());
		task.setType(taskType);


		Document metaDoc = ParamParser.parseXmlToDocument(task.getMetadata(), null);
		Node manualNode = metaDoc.selectSingleNode("/meta/metaset[@type='task_definition']/manual[text()='true']");
		if(manualNode== null){
			/*
			 * Tasks which do not require human intervention are set to transition_ready.
			 * Then, the WorkflowServer will promptly execute the default transition
			 * of this task.
			 */
			task.setProcstate(Constants.PROCSTATE_TRANSITION_READY);
		}
		else{
			task.setProcstate(Constants.PROCSTATE_TASK_TODO);
		}

		osdDao.makePersistent(task);

		// copy content of startTask
		taskDef.copyContent(repository.getName(), task);

		// create relation between workflow and start task
		/*
		 * Note: this is a normal relation, not a start_task_relation.
		 */
		RelationType taskRelType = rtDao.findByName(Constants.RELATION_TYPE_WORKFLOW_TO_TASK);
		relDao.findOrCreateRelation(taskRelType, workflow, task, "");

		return task;
	}

	/**
	 * Find open tasks of one or all workflows and for one or all users.
	 * @return
	 * {@code
	 *  <!-- identical to getObjects -->
	 * 	<objects>
	 * 	 <object>(serialized object)</object>
	 *  </objects>
	 * }
     * @param cmd Map with String,Value parameter pairs (taken from the HTTP Request):
	 * <h2>Parameters in HTTP Request</h2>
	 * <ul>
	 * <li>command=findopentasks</li>
	 * <li>[workflow_id]= optional: workflow_id</li>
	 * <li>[user_id] = optional: only return tasks for this user_id</li>
	 * <li>ticket=session ticket</li>
	 * </ul>
	 *
	 */
	@CinnamonMethod
	public Response findOpenTasks(Map<String,String> cmd){
		ObjectSystemData workflow;
		ObjectSystemDataDAO oDao = daoFactory.getObjectSystemDataDAO(em);
		UserDAO uDao = daoFactory.getUserDAO(em);

		ObjectTypeDAO otDao = daoFactory.getObjectTypeDAO(em);
		ObjectType taskObjectType = otDao.findByName(Constants.OBJTYPE_TASK);
		User owner;
		List<ObjectSystemData> tasks;

		/*
		 * 4 cases:
		 * 1. workflow defined, user defined = return this user's tasks in this workflow.
		 * 2. workflow defined, no user = return all tasks of this workflow.
		 * 3. no workflow given, but user define = return this user's tasks in all workflows.
		 * 4. no workflow given, no user defined: return all open tasks in all workflows.
		 */
		if(cmd.containsKey("workflow_id")){
			workflow = oDao.get(cmd.get("workflow_id"));
			if(workflow == null){
				throw new CinnamonException("error.workflow.not_found");
			}
			log.debug("found workflow");
			if(cmd.containsKey("user_id")){
				// case 1
				owner = uDao.get(cmd.get("user_id"));
				if(owner == null){
					throw new CinnamonException("error.user.not_found");
				}
				tasks = oDao.findAllByTypeAndProcstateAndOwnerAndRelationToLeftOsd(taskObjectType,
						Constants.PROCSTATE_TASK_TODO, owner, workflow);
			}
			else{
				// case 2
				tasks = oDao.findAllByTypeAndProcstateAndRelationToLeftOsd(taskObjectType,
						Constants.PROCSTATE_TASK_TODO, workflow);
			}
		}
		else if(cmd.containsKey("user_id")){
			// case 3
			owner = uDao.get(cmd.get("user_id"));
			if(owner == null){
				throw new CinnamonException("error.user.not_found");
			}
			tasks = oDao.findAllByTypeAndProcstateAndOwner(taskObjectType,
					Constants.PROCSTATE_TASK_TODO, owner);
		}
		else{
			log.debug("return all open tasks");
			// case 4
			tasks = oDao.findAllByTypeAndProcstate(taskObjectType, Constants.PROCSTATE_TASK_TODO);
		}

		XmlResponse resp = new XmlResponse(res);
	    Document doc = ObjectSystemData.generateQueryObjectResultDocument(tasks);
	    resp.setDoc(doc);
	    return resp;
	}

	/**
	 * Execute a transition from one task to another (or the end point of a workflow).
	 * The required parameters must be set beforehand in the task.
	 * @return
	 * {@code
	 * 	<success>transition.successful</success>
	 * }
     * @param cmd Map with String,Value parameter pairs (taken from the HTTP Request)
	 * <h2>Parameters in HTTP Request</h2>
	 * <ul>
	 * <li>command=dotransition</li>
	 * <li>id= id of task object</li>
	 * <li>transition_name = name of the selected transition</li>
	 * <li>ticket=session ticket</li>
	 * </ul>
	 *
	 */
	@CinnamonMethod
	public Response doTransition(Map<String,String> cmd){
		ObjectSystemDataDAO oDao = daoFactory.getObjectSystemDataDAO(em);
		ObjectSystemData task = oDao.get(cmd.get("id"));
		if(task == null){
			throw new CinnamonException("error.object.not.found");
		}
        Validator val = new Validator(user);
        val.validatePermissions(task, PermissionName.READ_OBJECT_CUSTOM_METADATA, PermissionName.WRITE_OBJECT_CUSTOM_METADATA, PermissionName.WRITE_OBJECT_SYS_METADATA);
        
		String transitionName = cmd.get("transition_name");
		String transitionXpath = String.format("/meta/metaset[@type='transition']/transition[name='%s']",transitionName);
		executeTransition(task, transitionXpath);
		
		XmlResponse resp = new XmlResponse(res);
		resp.addTextNode("success", "transition.successful");
	    return resp;	   
	}
	
	/**
	 * Execute the transition which can be found at the end of the given transitionXpath.
	 * @param task the task which holds the transition configuration in its metadata
	 * @param transitionXpath the optional transitionXpath param defines the xpath statement which returns the
     *                        transition that should be selected. If null, executeTransition uses the default
     *                        transition.
	 */
	public void executeTransition(ObjectSystemData task, String transitionXpath){
		if(transitionXpath == null){
			transitionXpath = "/meta/metaset[@type='transition']/transition[name=/meta/metaset[@type='transition']/default]";
		}
		ObjectSystemDataDAO oDao = daoFactory.getObjectSystemDataDAO(em);
		String meta = task.getMetadata();
		log.debug("transitionXPath: "+transitionXpath);
        log.debug("taskId: "+task.getId());
        log.debug("taskName:"+task.getName());
        log.debug("meta: "+task.getMetadata());
		Node transitionNode 
			= ParamParser.parseXmlToDocument(meta, null).selectSingleNode(transitionXpath);
        if(transitionNode == null){
            throw new RuntimeException("Could not find transition with xpath: "+transitionXpath);
        }
        Node transitionClassNode = transitionNode.selectSingleNode("class");
        if(transitionClassNode == null){
            throw new RuntimeException("Could not find transitionClassNode.");
        }
		String transitionClass = transitionClassNode.getText();
		log.debug("transitionClass: "+transitionClass);

		List<ObjectSystemData> newTasks;
		try {
			Transition transition = (Transition) Class.forName(transitionClass).newInstance();
			newTasks = transition.execute(task, transitionNode, repository);
		} catch (IllegalAccessException e) {
			throw new RuntimeException(e);
		} catch(InstantiationException e){
			throw new RuntimeException(e);
		} catch(ClassNotFoundException e){
			throw new CinnamonConfigurationException(e);
		}
		
		// create relation between workflow and new tasks
		RelationTypeDAO rtDao = daoFactory.getRelationTypeDAO(em);
		RelationType taskRelType = rtDao.findByName(Constants.RELATION_TYPE_WORKFLOW_TO_TASK);
		RelationDAO relDao = daoFactory.getRelationDAO(em);
		List<Relation> workflowRelations = relDao.findAllByRightAndType(task, taskRelType);
		/*
		 *  workflowRelations should only ever have one valid relation as a task
		 *  must have a relation to one and only one workflow.
		 */
		ObjectSystemData workflow = workflowRelations.get(0).getLeft();
		for(ObjectSystemData aTask : newTasks){
			relDao.findOrCreateRelation(taskRelType, workflow, aTask, "");
		}
        task.setProcstate(Constants.PROCSTATE_TASK_DONE);
		if(newTasks.isEmpty()){
			/*
			 *  if there are no new tasks, check if finished.
			 *  (a workflow is finished if there are no open tasks left)
			 */
			ObjectTypeDAO otDao = daoFactory.getObjectTypeDAO(em);
			ObjectType ot = otDao.findByName(Constants.OBJTYPE_TASK);
			List<ObjectSystemData> remainingTasks 
				= oDao.findAllByTypeAndProcstateAndRelationToLeftOsd(ot, 
					Constants.PROCSTATE_TASK_TODO, workflow);
			log.debug("remaining tasks: "+remainingTasks.size());
			if(remainingTasks.isEmpty() || (remainingTasks.size() == 1 && remainingTasks.contains(task))){
				log.debug(" => workflow is finished");
				workflow.setProcstate(Constants.PROCSTATE_WORKFLOW_FINISHED);
			}
		}		
	}
}
