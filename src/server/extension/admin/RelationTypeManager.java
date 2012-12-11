package server.extension.admin;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import server.CinnamonMethod;
import server.RelationType;
import server.dao.DAOFactory;
import server.dao.RelationTypeDAO;
import server.extension.BaseExtension;
import server.interfaces.CommandRegistry;
import server.interfaces.Response;
import server.response.XmlResponse;
import utils.ParamParser;

/**
 * This class provides methods for the cinnamon administrator to create and delete RelationTypes.
 * Note: Dandelion, the AdminTool, also does this
 * and more. But then, the methods here are more easily scripted via the safran client 
 * (which is useful for API tests that need an additional test user etc).
 * 
 */
public class RelationTypeManager extends BaseExtension{
	
	
	static {
		/*
		 * Required so BaseExtension can set the API-Class.
		 */
		setExtensionClass(RelationTypeManager.class);
	}
	
	private Logger log = LoggerFactory.getLogger(this.getClass());
	
	public CommandRegistry registerApi(CommandRegistry cmdReg) {
		return findAndRegisterMethods(cmdReg, this);
	}
	
	static DAOFactory daoFactory = DAOFactory.instance(DAOFactory.HIBERNATE);

	/**
	 * 
	 * Add a new relation type. RelationTypes define how a change to one object affects other
	 * objects that are associated to it via a relation.
	 * <h2>Parameters in HTTP Request</h2>
	 * <ul>
	 * <li>command=createrelationtype</li>
	 * <li>name=name of the relation</li>
	 * <li>description=describes this relation type</li>
	 * <li>leftobjectprotected=set to "true" or "false" to
     *  determine if the left object of the relation will be removed when the object to the right is deleted</li>
	 * <li>rightobjectprotected=set to "true" or "false" to
     *  determine if the object on the right side of the relation will be removed when the object on the left side is deleted</li>
     * <li>cloneOnLeftCopy=if set to "true", a relation of this type will be cloned for the copy of an object
     *      with a relation of this type.
     * </li>
     * <li>cloneOnRightCopy=if set to "true", a relation of this type will be cloned for the copy of an object
     *      with a relation of this type.</li>
	 * <li>ticket=session ticket</li>
	 * </ul>
	 * @return XML-Response:{@code
	 *	<relationTypeId>$id</relationTypeId> 
	 * }
	 * @param cmd HTTP request parameter map
	 */
	@CinnamonMethod
	public Response createRelationType(Map<String,String> cmd) {
		croakUnlessSuperuser("error.must_be_admin", getUser());
		
		RelationTypeDAO rtDao = daoFactory.getRelationTypeDAO(em);
		RelationType rt = new RelationType(cmd);
		rtDao.makePersistent(rt);
		
		XmlResponse resp = new XmlResponse(res);
		resp.addTextNode("relationTypeId", String.valueOf(rt.getId()));
		return resp;
	}
	
	/**
	 * Delete a relation type.
	 * @param cmd HTTP request parameter map:
	 * <ul>
	 * <li>command=deleterelationtype</li>
	 * <li>id=Id of relation type</li>
	 * <li>ticket=session ticket</li>
	 * </ul>
     * @return XML-Response:
	 * {@code
	 * 	<success>success.delete.relation_type</success>
	 * }
	 */
	@CinnamonMethod
	public Response deleteRelationType(Map<String,String> cmd) {
		croakUnlessSuperuser("error.must_be_admin", getUser());
		
		Long relationTypeId =
			ParamParser.parseLong(cmd.get("id"), "error.param.id");
		log.debug( "Trying to delete RelationType" + relationTypeId);

	    RelationTypeDAO relationTypeDAO = daoFactory.getRelationTypeDAO(em);
	    relationTypeDAO.delete(relationTypeId);

	    XmlResponse resp = new XmlResponse(res);
	    resp.addTextNode("success", "success.delete.relation_type");
    	return resp;
	}
	
}
