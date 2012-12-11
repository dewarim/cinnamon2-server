package server.extension.admin;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import server.CinnamonMethod;
import server.Group;
import server.User;
import server.dao.DAOFactory;
import server.dao.GroupDAO;
import server.dao.UserDAO;
import server.extension.BaseExtension;
import server.interfaces.CommandRegistry;
import server.interfaces.Response;
import server.response.XmlResponse;
import utils.ParamParser;

/**
 * This class provides methods to create and delete users. Note: Dandelion, the AdminTool, also does this
 * and more. But then, the methods here are more easily scripted via the safran client 
 * (which is useful for API tests that need an additional test user etc).
 * 
 */
public class UserManager extends BaseExtension{
	
	/* 
	 * UserManagement is an optional extension class which contains methods useful for user management
	 * (for example, in a testing environment). You should not add generic Cinnamon api methods here
	 * (like getUser). 
	 */
	
	static {
		/*
		 * Required so BaseExtension can set the API-Class.
		 */
		setExtensionClass(UserManager.class);
	}
	
	private Logger log = LoggerFactory.getLogger(this.getClass());
	
	public CommandRegistry registerApi(CommandRegistry cmdReg) {
		return findAndRegisterMethods(cmdReg, this);
	}
	
	static DAOFactory daoFactory = DAOFactory.instance(DAOFactory.HIBERNATE);
	
	/**
	 * Create a new user.
	 * The createuser command must be issued by a member of the <em>superuser</em> group.
     * <br>
     * Note: currently, this method does not create a home folder or any other system objects for the user
     * beside the user's personal group.
	 * <br>
     * @param cmd HTTP request parameter map:
	 * <ul>
	 * <li>command="createuser"</li>
	 * <li>ticket=session ticket</li>
	 * <li>description=Description of user, eg. a job title etc</li>
	 * <li>fullname=Real name</li>
	 * <li>name=login name</li>
     * <li>email=user's email</li>
	 * <li>pwd=this user's password</li>
	 * </ul>
	 * @return a Response object which contains:
	 * <pre>
	 * {@code
	 * 	<userId>$id</userId>
	 * }
	 * </pre>
	 */
	@CinnamonMethod
	public Response createUser(Map<String,String> cmd) {
        croakUnlessSuperuser("error.must_be_admin", getUser());
		
        XmlResponse resp = new XmlResponse(res);      
        
		User newUser = new User(cmd);
		UserDAO userDAO = daoFactory.getUserDAO(em);
		userDAO.makePersistent(newUser);

		log.debug("newUser: "+newUser.getId());
		
		String groupName = "_"+newUser.getId()+"_"+newUser.getName();
		Group newGroup = new Group(groupName, "userGroup", true, null);
			
		GroupDAO groupDAO = daoFactory.getGroupDAO(em);
		groupDAO.makePersistent(newGroup);
		groupDAO.addToUser(newUser.getId(), newGroup.getId());
		em.flush();

		resp.addTextNode("userId", String.valueOf(newUser.getId()));
		return resp;
	}
	
	/**
	 * Delete a user.
	 * The deleteuser command must be issued by a member of the <em>superusers</em> group.
     * <br>
	 * Note: this method is expected to fail if the user is still referenced by any objects
     * or folders.
     * @param cmd HTTP request parameter map
	 * <ul>
	 * <li>command=deleteuser</li>
	 * <li>user_id=id of the user you want to delete</li>
	 * <li>ticket=session ticket</li>
	 * </ul>
	 * 
	 * @return a Response containing
	 * <pre>
	 * 	{@code
	 * 	 <success>success.delete.user</success>
	 * 	}
	 * </pre>
	 * or an error node.
	 */
	@CinnamonMethod
	public Response deleteUser(Map<String,String> cmd) {
		croakUnlessSuperuser("error.must_be_admin", getUser());
		XmlResponse resp = new XmlResponse(res);

		Long userId = ParamParser.parseLong(cmd.get("user_id"), "error.param.user_id");
		log.debug("Trying to delete User "+userId);

	    UserDAO userDAO = daoFactory.getUserDAO(em);
		userDAO.delete(userId);
		
		log.debug("User deleted.");
		resp.addTextNode("success", "success.delete.user");
    	return resp;
	}	

}
