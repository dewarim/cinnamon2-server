package server.extension.admin;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import server.CinnamonMethod;
import server.dao.DAOFactory;
import server.dao.LifeCycleDAO;
import server.dao.LifeCycleStateDAO;
import server.exceptions.CinnamonException;
import server.extension.BaseExtension;
import server.interfaces.CommandRegistry;
import server.interfaces.Response;
import server.lifecycle.IState;
import server.lifecycle.LifeCycle;
import server.lifecycle.LifeCycleState;
import server.response.XmlResponse;
import utils.ParamParser;

import java.util.Map;

/**
 * This class provides methods for the cinnamon administrator to create and delete lifecycles and states.
 * Note: Dandelion, the AdminTool, also does this.
 * But then, the methods here are more easily scripted via the safran client
 * (which is useful for API tests that need special lifecycles etc).
 * 
 */
public class LifeCycleManager extends BaseExtension{
	
	
	static {
		/*
		 * Required so BaseExtension can set the API-Class.
		 */
		setExtensionClass(LifeCycleManager.class);
	}
	
	private Logger log = LoggerFactory.getLogger(this.getClass());
	
	public CommandRegistry registerApi(CommandRegistry cmdReg) {
		return findAndRegisterMethods(cmdReg, this);
	}
	
	static DAOFactory daoFactory = DAOFactory.instance(DAOFactory.HIBERNATE);

	/**
	 * Create a new lifecycle.
	 * @param cmd HTTP request parameter map:
	 * <ul>
	 * <li>command=createlifecycle</li>
	 * <li>name=Name of lifecycle</li>
	 * <li>ticket=session ticket</li>
	 * </ul>
     * @return XML-Response:
	 * {@code
	 * 	<lifeCycleId>$id</lifeCycleId>
	 * }
	 * 
	 */
	@CinnamonMethod
	public Response createLifeCycle(Map<String,String> cmd) {
		croakUnlessSuperuser("error.must_be_admin", getUser());

        LifeCycle lifeCycle = new LifeCycle();
        lifeCycle.setName(cmd.get("name"));
        LifeCycleDAO lcDao = daoFactory.getLifeCycleDAO(em);
        lcDao.makePersistent(lifeCycle);

		XmlResponse resp = new XmlResponse(res);
		resp.addTextNode("lifeCycleId", String.valueOf(lifeCycle.getId()) );
		return resp;
	}
	
	/**
	 * Deletes an unused lifecycle from the current repository.<br>
     * @param cmd HTTP request parameter map
	 * <ul>
	 * <li>command=deletelifecycle</li>
	 * <li>lifecycle_id=id of the lifecycle to be deleted</li>
	 * <li>ticket=session ticket</li>
	 * </ul>
     * @return XML-Response
	 * {@code
	 * 	<success>success.delete.lifecycle</success>
	 * }
	 */
	@CinnamonMethod
	public Response deleteLifeCycle(Map<String,String> cmd) {
		croakUnlessSuperuser("error.must_be_admin", getUser());
    	Long id = ParamParser.parseLong(cmd.get("lifecycle_id"), "error.param.lifecycle_id");

		log.debug( "Trying to delete LifeCycle " + id);
        LifeCycleDAO lcDao = daoFactory.getLifeCycleDAO(em);
        lcDao.delete(id);
		XmlResponse resp = new XmlResponse(res);
		resp.addTextNode("success", "success.delete.lifecycle");
		return  resp;
	}

    /**
	 * Add a lifecycle state to a lifecycle.<br>
     * @param cmd HTTP request parameter map
	 * <ul>
	 * <li>command = addlifecyclestate</li>
	 * <li>lifecycle_id = id of the lifecycle</li>
	 * <li>name=name of the lifecycle state</li>
	 * <li>[lcs_for_copy_id] = id of the state to which a copy of the object should revert. Optional.</li>
     * <li>state_class = name of a class to be executed when this lifecycle state is entered / left. This class
     * must exist in the server's classpath.</li>
     * <li>[parameter] = parameter for the state_class. Should be an XML string. Optional, defaults to {@code <config />}</li>
     * <li>[set_default] = "true" or "false" - set this lifecycle state as the new default for the lifecycle.
	 * <li>ticket=session ticket</li>
	 * </ul>
     * @return XML-Response
	 * {@code
	 * 	<success>success.delete.lifecycle</success>
	 * }
	 */
    @CinnamonMethod
	public Response addLifeCycleState(Map<String,String> cmd) {
		croakUnlessSuperuser("error.must_be_admin", getUser());
    	Long id = ParamParser.parseLong(cmd.get("lifecycle_id"), "error.param.lifecycle_id");
        LifeCycleDAO lcDao = daoFactory.getLifeCycleDAO(em);
        LifeCycle lifeCycle = lcDao.get(id);
        LifeCycleState lcsForCopy = null;
        LifeCycleStateDAO lcsDao = daoFactory.getLifeCycleStateDAO(em);
        if(cmd.containsKey("lcs_for_copy_id")){
            lcsForCopy = lcsDao.get(cmd.get("lcs_for_copy_id"));
        }

        LifeCycleState state;
        try{
            IState iState = (IState) Class.forName(cmd.get("state_class")).newInstance();
            state = new LifeCycleState(cmd.get("name"), iState.getClass(), cmd.get("parameter"), lifeCycle, lcsForCopy );
            if(cmd.containsKey("set_default")){
                if(cmd.get("set_default").equals("true")){
                    lifeCycle.setDefaultState(state);
                }
            }
            lcsDao.makePersistent(state);
        }
        catch (Exception e){
            throw new CinnamonException(e);
        }

		XmlResponse resp = new XmlResponse(res);
		resp.addTextNode("lifeCycleStateId", String.valueOf(state.getId()));
		return  resp;
	}

    // TODO: deleteLCS from LC
}
