package server.extension.admin;

import java.util.Map;

import org.dom4j.Document;
import org.dom4j.Element;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import server.CinnamonMethod;
import server.Group;
import server.dao.DAOFactory;
import server.dao.GroupDAO;
import server.dao.UserDAO;
import server.exceptions.CinnamonException;
import server.extension.BaseExtension;
import server.interfaces.CommandRegistry;
import server.interfaces.Response;
import server.response.XmlResponse;
import utils.ParamParser;

/**
 * This class provides methods for the cinnamon administrator to create and delete Groups.
 * Note: Dandelion, the AdminTool, also does this
 * and more. But then, the methods here are more easily scripted via the safran client 
 * (which is useful for API tests that need an additional test user etc).
 * 
 */
public class GroupManager extends BaseExtension{
	
	
	static {
		/*
		 * Required so BaseExtension can set the API-Class.
		 */
		setExtensionClass(GroupManager.class);
	}
	
	private Logger log = LoggerFactory.getLogger(this.getClass());
	
	public CommandRegistry registerApi(CommandRegistry cmdReg) {
		return findAndRegisterMethods(cmdReg, this);
	}
	
	static DAOFactory daoFactory = DAOFactory.instance(DAOFactory.HIBERNATE);

	/**
	 * Create a group.<br>
	 * The creategroup command must be issued by a member of the ~~_superusers~~ group.<br>
	 * <h2>Parameters in HTTP Request</h2>
	 * <ul>
	 * <li>command=creategroup</li>
	 * <li>name= Name of Group</li>
	 * <li>description= Description</li>
	 * <li>parentid=ParentGroup (may be 0 for no ParentGroup)</li>
	 * <li>ticket=session ticket</li>
	 * </ul>
	 * 
	 * @return
	 * Id of created Group entry (as XML: //cinnamon/groupid), which is 0 on failure.
	 * 
	 * @param cmd
	 */
	
	@CinnamonMethod
	public Response createGroup(Map<String,String> cmd) {
		croakUnlessSuperuser("error.must_be_admin", getUser());
		XmlResponse resp = new XmlResponse(res);
		Document doc = resp.getDoc();
		Element root = doc.addElement( "cinnamon" );
					
		// do not allow someone to setup private user groups.
		if (cmd.containsKey("is_user")) {
			cmd.remove("is_user");
		}
			
		Group group = new Group(cmd);
		GroupDAO groupDao = daoFactory.getGroupDAO(em);
		if (cmd.containsKey("parentid") && ( ! cmd.get("parentid").equals("0")) ) {
			Long parentId = ParamParser.parseLong(cmd.get("parentid"), "error.param.parent_id");
			Group parent = groupDao.get(parentId);
			if (parent != null) {
				group.setParent(parent);
			}
		}
		groupDao.makePersistent(group);
		root.addElement("groupid").addText( String.valueOf(group.getId()) );

		log.debug( "Created Group: \n"+doc.asXML());
		return resp;
	}
	
	/** 
	 * Delete a group.<br>
	 * The deletgegroup command must be issued by a member of the ~~_superusers~~ group.
	 * <br>
	 * Return value: true (on success) or false (on failure)
	 * 
	 * <h2>Parameters in HTTP Request</h2>
	 * <ul>
	 * <li>command=deletegroup</li>
	 * <li>id= ID of group to delete</li>
	 * <li>ticket=session ticket</li>
	 * </ul>
	 * 
	 * @param cmd
	 */
	@CinnamonMethod
	public Response deleteGroup(Map<String,String> cmd) {
		croakUnlessSuperuser("error.must_be_admin", getUser());
		XmlResponse resp = new XmlResponse(res);
		Document doc = resp.getDoc();
		Element root = doc.addElement( "cinnamon" );
		
		GroupDAO groupDAO = daoFactory.getGroupDAO(em);
		Long id = ParamParser.parseLong(cmd.get("id"), "error.param.id");
		Group exists = groupDAO.get(id);
        if(exists == null){
            throw new CinnamonException("error.group.not.found");
        }
		log.debug("Found the group to delete: "+exists.getName());
		groupDAO.delete(id);

		root.addElement("result").addElement("value").addText("true");

		log.debug("Removed Group: \n"+doc.asXML());
		return resp;
	}
	
	/**
	 * addUserToGroup - add a user to a target group.
	 * @param cmd a HTTP request map contains the following parameters:
	 * <ul>
	 * <li>user_id = Id of the user</li>
	 * <li>group_id = Id of the group to which the user will be added.</li>
	 * </ul>
	 * @return an XML document containing the result as:
     * <pre>
     * {@code
     *  <addUserToGroup result="true"/>
     * }
     * </pre>
     * or a standard XML error message.
     */
    @CinnamonMethod
	public Response addUserToGroup(Map<String,String> cmd) {
		XmlResponse resp = new XmlResponse(res);
		Document result = resp.getDoc();
		Element root = result.addElement( "cinnamon" );
		
		croakUnlessSuperuser("error.must_be_admin", getUser());

		Long userId = ParamParser.parseLong(cmd.get("user_id"), "error.param.user_id");
		Long groupId =  ParamParser.parseLong(cmd.get("group_id"), "error.param.group_id");

		UserDAO userDAO = daoFactory.getUserDAO(em);
		userDAO.addToGroup(userId, groupId);
			
		root.addElement("addUserToGroup").addAttribute("result", "true");
    	resp.setDoc(result);
    	return resp;
	}
	
	/**
	 * removeUserFromGroup - remove a user from a target group.
	 * @param cmd a HTTP-request map which contains the following parameters:
	 * <ul>
	 * <li>user_id = Id of the user</li>
	 * <li>group_id = Id of the group from which the user will be removed.</li>
	 * </ul>
	 * @return an XML document containing the result as:
     * <pre>
     * {@code
     *  <removeUserFromGroup result="true"/>
     * }
     * </pre>
     * or a standard error message.
	 */
	@CinnamonMethod
	public Response removeUserFromGroup(Map<String,String> cmd) {
		XmlResponse resp = new XmlResponse(res);
		Document result = resp.getDoc();
		Element root = result.addElement( "cinnamon" );
		
		croakUnlessSuperuser("error.must_be_admin", getUser());

		Long userId = ParamParser.parseLong(cmd.get("user_id"), "error.param.user_id");
		Long groupId =  ParamParser.parseLong(cmd.get("group_id"), "error.param.group_id");

		GroupDAO groupDao = daoFactory.getGroupDAO(em);
		groupDao.removeUserFromGroup(userId, groupId);
		
		root.addElement("removeUserFromGroup").addAttribute("result", "true");
    	return resp;
	}
}
