package server.extension;

import org.dom4j.Document;
import org.dom4j.Element;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import server.CinnamonMethod;
import server.Permission;
import server.dao.DAOFactory;
import server.dao.LifeCycleDAO;
import server.dao.LifeCycleStateDAO;
import server.dao.ObjectSystemDataDAO;
import server.data.ObjectSystemData;
import server.data.Validator;
import server.exceptions.CinnamonException;
import server.global.PermissionName;
import server.interfaces.CommandRegistry;
import server.interfaces.Response;
import server.lifecycle.LifeCycle;
import server.lifecycle.LifeCycleState;
import server.response.XmlResponse;
import utils.ParamParser;

import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.List;

public class LifeCycleApi extends BaseExtension{

	static {
		/*
		 * Required so BaseExtension can set the API-Class.
		 */
		setExtensionClass(LifeCycleApi.class);
	}

	private Logger log = LoggerFactory.getLogger(this.getClass());

	public CommandRegistry registerApi(CommandRegistry cmdReg) {
		return findAndRegisterMethods(cmdReg, this);
	}

	static DAOFactory daoFactory = DAOFactory.instance(DAOFactory.HIBERNATE);

	/**
	 * Attach a LifeCycle to an object. The lifecycle will start with the
     * default state or the one specified by the client (the client's choice takes precedence).
     * If the default state of this lifecycle is undefined, the client must specify a valid
     * lifecycle state.
	 * <h2>Parameters in HTTP Request</h2>
	 * <ul>
	 * <li>command=attachlifecycle</li>
	 * <li>lifecycle_id= the id of the lifecycle</li>
     * <li>[lifecycle_state_id] = optional: the state of the lifecycle. If not set, use the defaultState</li>
	 * <li>id= the id of the object</li>
	 * <li>ticket=session ticket</li>
	 * </ul>
     * @return a CinnamonException on failure or a Response object with
     * the following XML content:
     * <pre>
     * {@code
     *  <success>success.attach_lifecycle</success>
     * }
     * </pre>
     *
	 * <h2>Needed permissions</h2>
	 * WRITE_OBJECT_SYS_METADATA
	 * @param cmd a Map of HTTP request parameters
	 */
	@CinnamonMethod(checkTrigger = "true")
	public Response attachLifeCycle(Map<String,String> cmd) {
        LifeCycleDAO lcDao = daoFactory.getLifeCycleDAO(em);
        LifeCycle lifeCycle = lcDao.get(ParamParser.parseLong(cmd.get("lifecycle_id"),"error.param.lifecycle_id"));
//        LifeCycle lifeCycle = lcDao.get(cmd.get("lifecycle_id")); // won't compile with build script. Does compile in editor, though.
        ObjectSystemDataDAO oDao = daoFactory.getObjectSystemDataDAO(em);
//        ObjectSystemData osd = oDao.getOsdNotNull(cmd.get("id"));
        ObjectSystemData osd = oDao.get(cmd.get("id"));
        if(osd == null){
            throw new CinnamonException("error.object.not.found");
        }
        new Validator(getUser()).validateSetSysMeta(osd);

        LifeCycleState lifeCycleState;
        if(cmd.containsKey("lifecycle_state_id")){
            LifeCycleStateDAO lcsDao = daoFactory.getLifeCycleStateDAO(em);
            lifeCycleState = lcsDao.get(ParamParser.parseLong(cmd.get("lifecycle_state_id"),"error.param.lifecycle_state_id"));

//            lifeCycleState = lcsDao.get(cmd.get("lifecycle_state_id")); // won't compile with build script.
        }
        else if(lifeCycle.getDefaultState() != null){
            lifeCycleState = lifeCycle.getDefaultState();
        }
        else{
            throw new CinnamonException("error.undefined.lifecycle_state");
        }

        lifeCycleState.enterState(osd, lifeCycleState, repository, user);

        XmlResponse resp = new XmlResponse(res);
        resp.addTextNode("success", "success.attach_lifecycle");
        return resp;
	}

    /**
     * Removes all lifecycle state information from an object.
     * <h2>Parameters in HTTP Request</h2>
	 * <ul>
	 * <li>command=detachlifecycle</li>
	 * <li>id = the id of the object whose lifecycle state should be nulled.</li>
	 * <li>ticket=session ticket</li>
	 * </ul>
     * @return a CinnamonException on failure or a Response object with
     * the following XML content:
	 * {@code
     * <pre>
     *  <success>success.detach_lifecycle</success>
     * </pre>
     * }
	 *
	 * <h2>Needed permissions</h2>
	 * WRITE_OBJECT_SYS_METADATA
	 * @param cmd  a Map of HTTP request parameters
	 */
    @CinnamonMethod(checkTrigger = "true")
	public Response detachLifeCycle(Map<String,String> cmd) {
        ObjectSystemDataDAO oDao = daoFactory.getObjectSystemDataDAO(em);
//        ObjectSystemData osd = oDao.getOsdNotNull(cmd.get("id"));   // does not compile in build script. _does_ compile in editor.
        ObjectSystemData osd = oDao.get(cmd.get("id"));
        if(osd == null){
            throw new CinnamonException("error.object.not.found");
        }
        new Validator(getUser()).validateSetSysMeta(osd);

        osd.getState().exitState(osd, null, repository, user);
        osd.setState(null);
        XmlResponse resp = new XmlResponse(res);
        resp.addTextNode("success", "success.detach_lifecycle");
        return resp;
	}

    /**
	 * Change the lifecycle state of an object by moving to another state.
     * This method checks if the move is allowed and executes the lifecycle state
     * class of the old and new state, calling the exit() and enter() methods
     * as appropriate.
	 * <h2>Parameters in HTTP Request</h2>
	 * <ul>
	 * <li>command=changestate</li>
	 * <li>id = the id of the OSD whose state should be changed</li>
	 * <li>lifecycle_state_id = the id of the target lifecycle state</li>
	 * <li>[state_name] = the name of a target lifecycle state of the current lifecycle (may be used instead of the lifecycle_state_id)</li>
	 * <li>ticket=session ticket</li>
	 * </ul>
     * @return a CinnamonException on failure or a Response object with
     * the following XML content:
     *  <pre>
     * {@code
     *   <success>success.changed_state</success>
     * }
     *  </pre>
     *
	 * <h2>Needed permissions</h2>
	 * WRITE_OBJECT_SYS_METADATA
	 * @param cmd  a Map of HTTP request parameters
	 */
    @CinnamonMethod(checkTrigger = "true")
	public Response changeState(Map<String,String> cmd) {
       /*
        * get osd
        * validate permission
        * get target state
        * check target state with current state - is this transition allowed?
        * checkEnteringObject
        * call exit() on old state
        * call enter() on new state
        * set new state on OSD
        */
        ObjectSystemDataDAO oDao = daoFactory.getObjectSystemDataDAO(em);
        ObjectSystemData osd = oDao.get(cmd.get("id"));
        if(osd == null){
            throw new CinnamonException("error.object.not.found");
        }
        new Validator(getUser()).validateSetSysMeta(osd);

        LifeCycleStateDAO lcsDao = daoFactory.getLifeCycleStateDAO(em);
        LifeCycleState lifeCycleState;
        if(cmd.containsKey("state_name")){
            lifeCycleState = lcsDao.findByName(cmd.get("state_name"), osd.getState().getLifeCycle());
            if(lifeCycleState == null){
                throw new CinnamonException("error.param.state_name");
            }
        }
        else{
            lifeCycleState = lcsDao.get(ParamParser.parseLong(cmd.get("lifecycle_state_id"),"error.param.lifecycle_state_id"));
        }

        osd.getState().exitState(osd, lifeCycleState, repository, user);
        lifeCycleState.enterState(osd, lifeCycleState, repository, user); // if this fails, rollback occurs.

        XmlResponse resp = new XmlResponse(res);
        resp.addTextNode("success", "success.change_lifecycle");
        return resp;
	}

    /**
	 * Fetch a list of allowed exit states for a given object with
     * an attached lifecycle. 
	 * <h2>Parameters in HTTP Request</h2>
	 * <ul>
	 * <li>command=getnextstates</li>
	 * <li>id=id of the osd whose state may be changed</li>
	 * <li>ticket=session ticket</li>
	 * </ul>
	 * @return
	 * ?
	 *
	 * <h2>Needed permissions</h2>
     * READ_OBJECT_SYS_METADATA
	 * @param cmd  a Map of HTTP request parameters
	 */
    @CinnamonMethod
	public Response getNextStates(Map<String,String> cmd) {
        /*
         * get OSD
         * get lifecycle
         * get lifecycle states
         * call checkEnteringObject
         * Note: checkExit is undefined at the moment.
         */
        ObjectSystemDataDAO oDao = daoFactory.getObjectSystemDataDAO(em);
        ObjectSystemData osd = oDao.get(cmd.get("id"));
        if(osd == null){
            throw new CinnamonException("error.object.not.found");
        }
        new Validator(getUser()).validatePermission(osd.getAcl(), PermissionName.READ_OBJECT_SYS_METADATA);

        LifeCycleState state = osd.getState();
        if(state == null){
            throw new CinnamonException("error.no_lifecycle_set");
        }

        Collection<LifeCycleState> states = new HashSet<LifeCycleState>();
        states.addAll(osd.getState().getLifeCycle().getStates());
        states.remove(osd.getState());

        XmlResponse resp = new XmlResponse(res);
        Element root = resp.getDoc().addElement("lifecycle-states");
        for(LifeCycleState lcs : states){
            if(lcs.openForEntry(osd)){
                lcs.toXmlElement(root);
            }
        }
        return resp;
	}

    /**
     * <h2>Parameters in HTTP Request</h2>
	 * <ul>
	 * <li>command=listlifecycles</li>
	 * <li>ticket=session ticket</li>
	 * </ul>
     * Get all lifecycle objects from the database and return
     * them as an XML list.
     * @param cmd  a Map of HTTP request parameters
     * @return a Response object which sends an XML document to the user which
     * contains all lifecycle objects.
     */
    @CinnamonMethod
	public Response listLifeCycles(Map<String,String> cmd) {
        LifeCycleDAO lDao = daoFactory.getLifeCycleDAO(em);
		List<LifeCycle> cycles = lDao.list();

		XmlResponse resp = new XmlResponse(res);
		Element root = resp.getDoc().addElement("lifecycles");
		for(LifeCycle lc : cycles){
			lc.toXmlElement(root);
		}
		return resp;
	}

    /**
     * <h2>Parameters in HTTP Request</h2>
	 * <ul>
	 * <li>command=getlifecycle</li>
	 * <li>name = Name of the lifecycle - you must specify either the name or the id.</li>
	 * <li>id = Id of the lifecycle - you must specify either the name or the id.</li>
	 * <li>ticket=session ticket</li>
	 * </ul>
     * Fetch a specific lifecycle by name or id from the database as XML. If both id and name
     * parameters are set, it will ignore the name parameter.
     * @param cmd  a Map of HTTP request parameters
     * @return a Response object which sends an XML document to the user which
     * contains the requested lifecycle object (or an error message if the lifecycle could not be found).<br>
     *     Example:
     * <pre>
     *  {@code
     *  <lifecycles>
     *      <lifecycle>
     *          <id>1234</id>
     *          <name>DemoLC</name>
     *          <sysName>lifecycle.demo</sysName>
     *          <defaultState> ... (if set, contains serialized LCS)</defaultState>
     *          <states>
     *            <lifecycleState>
     *              <id>543</id>
     *              <name>TestState</name>
     *              <sysName>example.test.state</sysName>
     *              <stateClass>server.lifecycle.state.NopState</stateClass>
     *              <parameter>&lt;config /&gt;</parameter> (encoded XML string)
     *              <lifeCycle>44</lifeCycle> (may be empty)
     *              <lifeCycleStateForCopy>7</lifeCycleStateForCopy> (may be empty)
     *            </lifecycleState>
     *            ... (other states)
     *          </states>
     *      </lifecycle>
     *  </lifecycles>
     *  }
     * </pre>
     */
    @CinnamonMethod
	public Response getLifeCycle(Map<String,String> cmd) {
        LifeCycleDAO lDao = daoFactory.getLifeCycleDAO(em);
        LifeCycle lifeCycle;
        if(cmd.containsKey("id")){
            lifeCycle = lDao.get(cmd.get("id"));
        }
        else if(cmd.containsKey("name")){
		    lifeCycle = lDao.findByName(cmd.get("name"));
        }
        else{
            throw new CinnamonException("error.param.lc.missing");
        }

        if(lifeCycle == null){
            throw new CinnamonException("error.lifecycle.missing");
        }

		XmlResponse resp = new XmlResponse(res);
		Element root = resp.getDoc().addElement("lifecycles");
		lifeCycle.toXmlElement(root);
		return resp;
	}

        /**
     * <h2>Parameters in HTTP Request</h2>
	 * <ul>
	 * <li>command=getlifecyclestate</li>
	 * <li>id = Id of the lifecycle state.</li>
	 * <li>ticket=session ticket</li>
	 * </ul>
     * Fetch a specific lifecycle state by id from the database as XML.
     * @param cmd  a Map of HTTP request parameters
     * @return a Response object which sends an XML document to the user which
     * contains the requested lifecycle state object (or an error message if it could not be found).<br>
     *     Example:
     * <pre>
     *  {@code
     *          <states>
     *            <lifecycleState>
     *              <id>543</id>
     *              <name>TestState</name>
     *              <sysName>example.test.state</sysName>
     *              <stateClass>server.lifecycle.state.NopState</stateClass>
     *              <parameter>&lt;config /&gt;</parameter> (encoded XML string)
     *              <lifeCycle>44</lifeCycle> (may be empty)
     *              <lifeCycleStateForCopy>7</lifeCycleStateForCopy> (may be empty)
     *            </lifecycleState>
     *          </states>
     *  }
     * </pre>
     */
    @CinnamonMethod
	public Response getLifeCycleState(Map<String,String> cmd) {
        LifeCycleStateDAO lDao = daoFactory.getLifeCycleStateDAO(em);
        LifeCycleState lifeCycleState;
        if(cmd.containsKey("id")){
            lifeCycleState = lDao.get(cmd.get("id"));
        }
        else{
            throw new CinnamonException("error.param.lcs.id");
        }

        if(lifeCycleState == null){
            throw new CinnamonException("error.object.not.found");
        }

		XmlResponse resp = new XmlResponse(res);
		Element root = resp.getDoc().addElement("states");
		lifeCycleState.toXmlElement(root);
		return resp;
	}

}