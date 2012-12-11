package workflow;

import java.text.DecimalFormat;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import javax.persistence.EntityManager;
import javax.persistence.EntityTransaction;
import javax.persistence.Query;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import server.ObjectType;
import server.Relation;
import server.RelationType;
import server.dao.*;
import server.data.ObjectSystemData;
import server.exceptions.CinnamonException;
import server.extension.WorkflowApi;
import server.global.ConfThreadLocal;
import server.global.Constants;
import server.i18n.Language;
import server.i18n.LocalMessage;
import server.i18n.UiLanguage;
import server.index.LuceneBridge;
import server.index.ResultCollector;
import server.interfaces.Repository;
import utils.HibernateSession;

public class WorkflowServer implements Runnable {

	private Logger log = LoggerFactory.getLogger(this.getClass());
	
	EntityManager em;
	static DAOFactory daoFactory = DAOFactory.instance(DAOFactory.HIBERNATE);
	Long sleep;
	Integer itemsPerRun;
	Repository repository;
	ObjectType taskObjectType = null;
	ObjectType workflowObjectType;
	ObjectType workflowTemplateType;
	String queryDeadline;
	String queryWorkflowDeadline;
	ObjectSystemDataDAO oDao;
	ObjectTypeDAO otDao;
	
	final String deadlineQueryFormatString = "<FilteredQuery>" +
	"<Filter>" +
	"<RangeFilter fieldName='workflow_deadline' lowerTerm='00000000000000000000' upperTerm='%s'></RangeFilter>" +
	"</Filter>" +
	"<Query><BooleanQuery>"+
	"<Clause occurs='must'><TermQuery fieldName='objecttype'>%s</TermQuery></Clause>" +
	"<Clause occurs='must'><TermQuery fieldName='procstate'>%s</TermQuery></Clause>" +
	"</BooleanQuery></Query>" +
	"</FilteredQuery>";
	
	public WorkflowServer(Repository repository){
		this.repository = repository;
		ConfThreadLocal conf = ConfThreadLocal.getConf();
		String sleepTime 
			= conf.getField(String.format("repository[name='%s']/workflowServer/sleepBetweenRuns", 
					repository.getName()), "5000");
		this.sleep = Long.parseLong(sleepTime);
	}
	
	void initializeDaos(){		
		taskObjectType = otDao.findByName(Constants.OBJTYPE_TASK);
        if(taskObjectType == null){
            log.error("Could not find the essential ObjectType "+Constants.OBJTYPE_TASK);
        }
		queryDeadline = buildDeadlineQuery(taskObjectType, Constants.PROCSTATE_TASK_TODO);
		
		workflowObjectType = otDao.findByName(Constants.OBJTYPE_WORKFLOW);
        if(workflowObjectType == null){
            log.error("Could not find the essential ObjectType "+Constants.OBJTYPE_WORKFLOW);
        }

        workflowTemplateType = otDao.findByName(Constants.OBJTYPE_WORKFLOW_TEMPLATE);
        if(workflowTemplateType == null){
            log.error("Could not find the essential ObjectType "+Constants.OBJTYPE_WORKFLOW_TEMPLATE);
        }
		queryWorkflowDeadline = buildDeadlineQuery(workflowObjectType, Constants.PROCSTATE_WORKFLOW_STARTED);
		oDao = daoFactory.getObjectSystemDataDAO(em);
	}
	
	@Override
	/**
	 * Run every $sleep seconds
	 */
	public void run() {
		// initialize LocalMessage:
		// this is necessary if a task needs to create localized messages.
		// TODO: this will not work with an uninitialized database.
		log.debug("WorkflowServerThread: started");
		while(true){
			try{
				if(em == null || ! em.isOpen()){
					log.debug("EntityManager is null or not open - fetching a new one.");
					em = repository.getEntityManager();
					HibernateSession.setLocalEntityManager(em);
				}
				otDao = daoFactory.getObjectTypeDAO(em);
				if(taskObjectType == null){
					/*
					 * If the taskObjectType is null and the Task-ObjectType cannot be found,
					 * the db is not yet in a workable state. 
					 * We wait for 10s before trying again. 
					 */
					log.debug("taskObjectType seems uninitialized.");				
					taskObjectType = otDao.findByName(Constants.OBJTYPE_TASK);
//                    Query q = em.createQuery("select o from ObjectType o where o.name=:name");
//                    q.setParameter("name", Constants.OBJTYPE_TASK);
//                    taskObjectType = (ObjectType) q.getSingleResult();
					if(taskObjectType == null){
						log.debug("Workflow system is not initialized. Sleep for 10 seconds.");
						doSleep(10000L);
						continue;
					}
					else{
						log.debug("initializeDaos");
						initializeDaos();
						initializeLocalMessage();
					}
				}
				log.debug("going to sleep");
				doSleep(sleep);			
				EntityTransaction et = null;
				try{
					log.debug("WorkflowServerThread awakened");
					// start transaction				
					et = em.getTransaction();
					et.begin();

                    if(oDao == null || oDao.sessionIsClosed()){
                        // This probably only happens if initialization failed in the first place.
                        // But at least this way the critical error messages will surface in the log repeatedly.
                        log.debug("oDao is null - trying to initialize Daos again.");
                        initializeDaos();
                    }

					// find tasks with procstate transition_ready.
					List<ObjectSystemData> transitionList
					= oDao.findAllByTypeAndProcstate(taskObjectType, Constants.PROCSTATE_TRANSITION_READY);
					log.debug("TransitionReady tasks found: "+transitionList.size());
					WorkflowApi wfApi = new WorkflowApi(repository, em);
					tryAndExecuteTransitions(wfApi, transitionList, null);

					// find all tasks with passed deadline:
					// build query

					LuceneBridge lucene = repository.getLuceneBridge();
					ResultCollector results = lucene.search(queryDeadline);
					Collection<ObjectSystemData> deadlinedTasks = results.getSearchResultsAsOSDs();
					log.debug("tasks with deadline: "+deadlinedTasks.size());
					tryAndExecuteTransitions(wfApi, deadlinedTasks,
					"/meta/metaset[@type='transition']/transition[name='deadline_transition']");

					// find all workflows with passed deadline:
					checkWorkflowDeadlines(wfApi);

					// end transaction
					et.commit();
					log.debug("WorkflowServerThread returns to sleep.");
				}
				catch (Exception e) {
					log.debug("WorkflowServer-Thread encountered an error:",e);
					try {
						/*
						 *  try to rollback any changes to items,
						 *  for example invalid indexed-time column.
						 */					
						if (et != null && et.isActive()) {						
							et.rollback();
						}					
					} catch (Exception re) {
						log.error("Failed to rollback; "+re.getMessage());
					}
					// TODO: handle exception
				}
			}
			catch (Exception e) {
				log.debug("",e);
			}
		}
			
	}
	
	void doSleep(Long s){
		try {
			Thread.sleep(s);
		} catch (Exception e) {
			log.debug("Sleep of WorkflowServer was interrupted.");
		}
	}
	
	void tryAndExecuteTransitions(WorkflowApi wfApi, Collection<ObjectSystemData> tasks, String transitionXpath){
		for(ObjectSystemData task : tasks){
			try{
				wfApi.executeTransition(task, transitionXpath );
			}
			catch (Exception e) {
				log.error("Failed to execute Transition",e);
			}
		}
	}
	
	void checkWorkflowDeadlines(WorkflowApi wfApi){
		/* 
		 * search for workflows that have reached their deadline
		 * get workflow-template
		 * get deadline-taskdef for each
		 * create deadline-task for workflow
		 * set deadline-task to transition_ready if it's an automatic task
		 * 	(last is done by wfApi.createTask)
		 * The new deadline-Task should transition automatically to its default transition.
		 */
		LuceneBridge lucene = repository.getLuceneBridge();
		ResultCollector results = lucene.search(queryWorkflowDeadline);
		Collection<ObjectSystemData> deadlinedWorkflows = results.getSearchResultsAsOSDs();
//        log.debug("deadlinedWorkflows: "+deadlinedWorkflows.size());
		RelationTypeDAO rtDao = daoFactory.getRelationTypeDAO(em);
		RelationType deadlineRelationType = rtDao.findByName(Constants.RELATION_TYPE_WORKFLOW_TO_DEADLINE_TASK);
		RelationDAO relDao = daoFactory.getRelationDAO(em);
		for(ObjectSystemData workflow : deadlinedWorkflows){
			// possible optimization-1: preload the templates.
			// possible optimization-2: put the id of the deadline-taskdef into the workflow instance.
//            log.debug("WorkflowTemplateType: "+workflowTemplateType.getId());
			List<ObjectSystemData> templates = oDao.findAllByNameAndType(workflow.getName(), workflowTemplateType);
			if(templates.size() != 1){
                String message = String.format("Found %d deadline templates - expected: one (with name '%s')!",
						templates.size(), workflow.getName());
				throw new CinnamonException(message);
			}			
			List<Relation> relations = relDao.findAllByLeftAndType(templates.get(0), deadlineRelationType);
			if(relations.size() != 1){
                String message = String.format("Found %d deadline relations - there can be only one!",
						relations.size());
				throw new CinnamonException(message);
			}
			ObjectSystemData deadlineTaskDef = relations.get(0).getRight();
			wfApi.createTask(deadlineTaskDef, workflow);
		}
		
	}
	
	/**
	 * Build a query for 'workflow_deadline' index depending on objecttype and procstate.
	 * For example, a deadline on a task object is irrelevant, if the procstate is "done".
	 * @param ot the objecttype of the items to find
	 * @param procstate the forbidden procstate
	 * @return an XML query string for use with LuceneBridge.search()
	 */
	String buildDeadlineQuery(ObjectType ot, String procstate){
        log.debug("buidlDeadlineQuery: objectType="+ot+" procstate="+procstate);
		String deadline = pad(new Date().getTime());
		return String.format(deadlineQueryFormatString, deadline, pad(ot.getId()), procstate);
	}
	
	// TODO: centralize the DecimalForamt & pad-routines.
	private static final DecimalFormat formatter =		
	    new DecimalFormat("00000000000000000000");

	public static String pad(Long n) {
	  return formatter.format(n);
	}
	
	void initializeLocalMessage(){
		log.debug("Initialize LocalMessage");
		try{
			MessageDAO messageDao = daoFactory.getMessageDAO(em);
			UiLanguageDAO languageDao = daoFactory.getUiLanguageDAO(em);
			UiLanguage language = languageDao.findByIsoCode("und");
			LocalMessage.initializeLocalMessage(messageDao, language);
			log.debug("LocalMessage initialized.");
		}
		catch (Exception e) {
			log.debug("Could not initialize LocalMessage: ",e);
		}
	}
}
