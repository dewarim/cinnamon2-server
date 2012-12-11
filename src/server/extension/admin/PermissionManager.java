package server.extension.admin;

import java.util.Map;

import org.dom4j.Element;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import server.CinnamonMethod;
import server.Permission;
import server.dao.DAOFactory;
import server.dao.PermissionDAO;
import server.exceptions.CinnamonException;
import server.extension.BaseExtension;
import server.interfaces.CommandRegistry;
import server.interfaces.Response;
import server.response.XmlResponse;
import utils.ParamParser;

/**
 * This class provides methods to create and delete Permissions. Note: Dandelion, the AdminTool, also does this
 * and more. But then, the methods here are more easily scripted via the safran client 
 * (which is useful for API tests that need an additional test user etc).
 * 
 */
public class PermissionManager extends BaseExtension{
	
	/* 
	 * PermissionManagement is an optional extension class which contains methods useful for permission
	 * management (for example, in a testing environment).
	 * You should not add generic Cinnamon api methods here
	 * (like getPermission and listPermissions). 
	 */
	
	static {
		/*
		 * Required so BaseExtension can set the API-Class.
		 */
		setExtensionClass(PermissionManager.class);
	}
	
	@SuppressWarnings("unused")
	private Logger log = LoggerFactory.getLogger(this.getClass());
	
	public CommandRegistry registerApi(CommandRegistry cmdReg) {
		return findAndRegisterMethods(cmdReg, this);
	}
	
	static DAOFactory daoFactory = DAOFactory.instance(DAOFactory.HIBERNATE);
	
	/**
	 * The createpermission command must be issued by a member of the <em>superusers</em>group.
     * @param cmd HTTP request parameter map:
	 * <ul>
	 * <li>command=createpermission</li>
	 * <li>name=name of Permission</li>
	 * <li>description=description of the Permission</li>
	 * <li>ticket=session ticket</li>
	 * </ul>
     * @return XML-Response representing the new Permission object - or an XML-wrapped error message.
	 */
	@CinnamonMethod
	public Response createPermission(Map<String,String> cmd) {
		croakUnlessSuperuser("error.must_be_admin", getUser());
		
		Permission permission = new Permission(cmd);   	
		PermissionDAO pDao = daoFactory.getPermissionDAO(em);
		pDao.makePersistent(permission);
		
		XmlResponse resp = new XmlResponse(res);
		Element root = resp.getDoc().addElement("permissions");
		permission.toXmlElement(root);
		return resp;
	}	

	/**
	 * @param cmd HTTP request parameter map
	 * <ul>
	 * <li>command=deletepermission</li>
	 * <li>id=id of the permission object</li>
	 * </ul>
	 * @return XML-String representing a success message - or an XML-wrapped error message.
	 */
	@CinnamonMethod
	public Response deletePermission(Map<String,String> cmd) {
		croakUnlessSuperuser("error.must_be_admin", getUser());		
		
		Long id = ParamParser.parseLong(cmd.get("id"), "error.param.id");
		PermissionDAO pDao = daoFactory.getPermissionDAO(em);
		Permission permission = pDao.get(id);
		if(permission == null){
			throw new CinnamonException("error.object.not.found");
		}
		pDao.makeTransient(permission);
		
		String msg = String.format("Permission with id %s was successfully deleted.", id);
		XmlResponse resp = new XmlResponse(res);
		resp.addTextNode("success",msg);
		return resp;
	}
}
