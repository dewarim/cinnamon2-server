package server.extension.admin;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import server.CinnamonMethod;
import server.ObjectType;
import server.dao.DAOFactory;
import server.dao.ObjectTypeDAO;
import server.extension.BaseExtension;
import server.interfaces.CommandRegistry;
import server.interfaces.Response;
import server.response.XmlResponse;
import utils.ParamParser;

/**
 * This class provides methods for the cinnamon administrator to create and delete ObjectTypes.
 * Note: Dandelion, the AdminTool, also does this
 * and more. But then, the methods here are more easily scripted via the safran client 
 * (which is useful for API tests that need an additional test user etc).
 * 
 */
public class ObjectTypeManager extends BaseExtension{
	
	
	static {
		/*
		 * Required so BaseExtension can set the API-Class.
		 */
		setExtensionClass(ObjectTypeManager.class);
	}
	
	private Logger log = LoggerFactory.getLogger(this.getClass());
	
	public CommandRegistry registerApi(CommandRegistry cmdReg) {
		return findAndRegisterMethods(cmdReg, this);
	}
	
	static DAOFactory daoFactory = DAOFactory.instance(DAOFactory.HIBERNATE);

	/**
	 * The createobjecttype command must be issued by a member of the <em>superusers</em>group.
     * @param cmd HTTP request parameter map
	 * <ul>
	 * <li>command=createobjecttype</li>
	 * <li>name=name of objecttype</li>
	 * <li>description=description of the new objecttype</li>
	 * <li>ticket=session ticket</li>
	 * </ul>
     * @return XML response with id of objectType:
	 * {@code
	 * 	<objectTypeId>$id</objectTypeId>
	 * }
	 */
	@CinnamonMethod
	public Response createObjectType(Map<String,String> cmd) {
		croakUnlessSuperuser("error.must_be_admin", getUser());

		ObjectType objectType = new ObjectType(cmd);   	
		ObjectTypeDAO objectTypeDAO = daoFactory.getObjectTypeDAO(em);
		objectTypeDAO.makePersistent(objectType);
		XmlResponse resp = new XmlResponse(res);
		resp.addTextNode("objectTypeId",String.valueOf(objectType.getId()) );
		return resp;
	}
	
	/**
	 * Delete an ObjectType.
	 * Note: the client should warn the user if objects become inaccessible.
	 * @param cmd HTTP request parameter Map:<br>
	 * 				<ul>
	 * 		<li>id = Id of the ObjectType (integer)</li>
	 * 				</ul>
     * @return XML Response {@code <success>success.delete.object_type</success>}
	 */
	@CinnamonMethod
	public Response deleteObjectType(Map<String,String> cmd) {
		croakUnlessSuperuser("error.must_be_admin", getUser());
		Long objectTypeID = ParamParser.parseLong(cmd.get("id"), "error.param.id");

		log.debug( "Trying to delete ObjectType " + objectTypeID);

	    ObjectTypeDAO objectTypeDAO = daoFactory.getObjectTypeDAO(em);
	    objectTypeDAO.delete(objectTypeID);
	    log.debug( "Deleted ObjectType.");
	    XmlResponse resp = new XmlResponse(res);
		resp.addTextNode("success","success.delete.object_type");
	    return resp;
	}
}
