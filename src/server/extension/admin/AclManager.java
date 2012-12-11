package server.extension.admin;

import java.util.Map;

import org.dom4j.Document;
import org.dom4j.Element;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import server.Acl;
import server.AclEntry;
import server.AclEntryPermission;
import server.CinnamonMethod;
import server.Permission;
import server.dao.AclDAO;
import server.dao.AclEntryDAO;
import server.dao.AclEntryPermissionDAO;
import server.dao.DAOFactory;
import server.dao.GroupDAO;
import server.dao.PermissionDAO;
import server.exceptions.CinnamonException;
import server.extension.BaseExtension;
import server.interfaces.CommandRegistry;
import server.interfaces.Response;
import server.response.XmlResponse;
import utils.ParamParser;

/**
 * This class provides methods to create and delete ACLs. Note: Dandelion, the AdminTool, also does this
 * and more. But then, the methods here are more easily scripted via the safran client 
 * (which is useful for API tests that need an additional test user etc).
 * 
 */
public class AclManager extends BaseExtension{
	
	/* 
	 * AclManagement is an optional extension class which contains methods useful for acl management
	 * (for example, in a testing environment). You should not add generic Cinnamon api methods here
	 * (like getAcl). 
	 */
	
	static {
		/*
		 * Required so BaseExtension can set the API-Class.
		 */
		setExtensionClass(AclManager.class);
	}
	
	private Logger log = LoggerFactory.getLogger(this.getClass());
	
	public CommandRegistry registerApi(CommandRegistry cmdReg) {
		return findAndRegisterMethods(cmdReg, this);
	}
	
	static DAOFactory daoFactory = DAOFactory.instance(DAOFactory.HIBERNATE);
	
	/**
	 * addGroupToAcl - add a group to an ACL (via AclEntry).
	 * Returns an XML document containing the result.
	 * @param cmd HTTP request parameter map
	 * The request contains the following parameters:
	 * <ul>
	 * <li>command	= "addgrouptoacl"</li>
	 * <li>acl_id	= id of the acl</li>
	 * <li>group_id	= id of the group</li>
	 * </ul>
     * @return Response object for HTTP response
	 */
	@CinnamonMethod
	public Response addGroupToAcl(Map<String,String> cmd) {
		XmlResponse resp = new XmlResponse(res);
		Document doc = resp.getDoc();
		Element root = doc.addElement("aclEntries");
		croakUnlessSuperuser("Only an admin may change acl settings.", getUser());

		Long aclId 		= ParamParser.parseLong(cmd.get("acl_id"), "error.param.acl_id");
		Long groupId 	= ParamParser.parseLong(cmd.get("group_id"), "error.param.group_id");
			
		GroupDAO groupDAO = daoFactory.getGroupDAO(em);
		AclEntry ae = groupDAO.addToAcl(aclId, groupId);
		ae.toXmlElement(root);
		return resp;
	}
	
	/**
	 * The createacl command must be issued by a member of the <em>superusers</em>group.
     * @param cmd HTTP request parameter map:
	 * <ul>
	 * <li>command=createacl</li>
	 * <li>name=name of acl</li>
	 * <li>description=description of the new acl</li>
	 * <li>ticket=session ticket</li>
     * </ul>
     * @return
     * xml-doc with the Acl: /acls/acl
     */
	@CinnamonMethod
	public Response createAcl(Map<String,String> cmd) {
		croakUnlessSuperuser("error.must_be_admin", getUser());
		XmlResponse resp = new XmlResponse(res);
		Element root = resp.getDoc().addElement("acls");
		
		Acl acl = new Acl(cmd);   	
		AclDAO aclDao = daoFactory.getAclDAO(em);
		aclDao.makePersistent(acl);
		
		acl.toXmlElement(root);
		return resp;
	}

	/**
	 * Delete an ACL.
	 * Note: the client should warn the user if objects become inaccessible.
	 * @return
	 * {@code
	 * 	<success>success.delete.acl</success> 
	 * }
	 * 
	 * @param cmd Map with Key/Value-Pair<br>
	 * <ul>
	 * 	<li>id = Id of the ACL (integer)</li>
	 * </ul>
	 */
	@CinnamonMethod
	public Response deleteAcl(Map<String,String> cmd) {
		croakUnlessSuperuser("error.must_be_admin", getUser());
		
		Long aclId = ParamParser.parseLong(cmd.get("id"), "error.param.id");
		log.debug( "Trying to delete ACL " + aclId);
		AclDAO aclDAO = daoFactory.getAclDAO(em);
		aclDAO.delete(aclId);
			
		log.debug( "Deleted ACL.");
		XmlResponse resp = new XmlResponse(res);
		resp.addTextNode("success", "success.delete.acl");
		return resp;
	}
	
	/**
	 * Set name and or description of an ACL
	 * @return
	 * {@code
	 *  <success>success.edit.acl</success>
	 * }
	 * @param cmd Map with Key/Value-Pairs<br>
	 * 		<ul>
	 * 		<li>acl_id = Id of the ACL (integer)</li>
	 * 		<li>[name]= new name</li>
	 * 		<li>[description] = new description</li>
	 * 		<li>ticket=session ticket (at least, if this method is called via http)</li> 	
	 * 	 	</ul>
	 */
	@CinnamonMethod
	public Response editAcl(Map<String,String> cmd) {
		croakUnlessSuperuser("error.must_be_admin", getUser());
		Long aclId = ParamParser.parseLong(cmd.get("acl_id"), "error.param.acl_id");
			
		// load object and change with given params:
		AclDAO aclDAO = daoFactory.getAclDAO(em);
		aclDAO.update(aclId, cmd);
						
		log.debug( "Changed ACL.");
		XmlResponse resp = new XmlResponse(res);
		resp.addTextNode("success", "success.edit.acl");
		return resp;
	}
	
	/**
	 * removeGroupFromAcl - remove a group from an acl.
	 * @param cmd HTTP request parameter map
	 * <ul>
	 * <li>command = removegroupfromacl</li>
	 * <li>group_id = ID of the group to remove</li>
	 * <li>acl_id   = ID of the acl to remove the group from</li>
	 * <li>ticket  = session ticket</li>
	 * </ul>
	 * @return
	 * {@code <success>group.removed_from_acl</success>} or xml-error-doc
	 * 
	 */
	@CinnamonMethod
	public Response removeGroupFromAcl(Map<String,String> cmd) {
        croakUnlessSuperuser("error.must_be_admin", getUser());
		Long aclID 		= ParamParser.parseLong(cmd.get("acl_id"), "error.param.acl_id");
		Long groupID 	= ParamParser.parseLong(cmd.get("group_id"), "error.param.group_id");
			
		GroupDAO groupDAO = daoFactory.getGroupDAO(em);
		groupDAO.removeFromAcl(aclID, groupID);
		
		XmlResponse resp = new XmlResponse(res);
		resp.addTextNode("success", "group.removed_from_acl");		
		return resp;
	}
	
	/**
	 * Add a Permission to an AclEntry
	 * @param cmd Map with Key/Value-Pair<br>
	 * <ul>
	 * 	<li>entry_id = Id of an AclEntry (integer)</li>
	 * 	<li>permission_id = Id of Permission</li>
	 * </ul>
	 * @return XML response object for HTTP response:
     * {@code <success>success.add.permission</success>}
	 */
	@CinnamonMethod
	public Response addPermissionToAclEntry(Map<String,String> cmd) {
		croakUnlessSuperuser("error.must_be_admin", getUser());
				
		Long aeId = ParamParser.parseLong(cmd.get("entry_id"), "error.param.id");
		AclEntryDAO aeDAO = daoFactory.getAclEntryDAO(em);
		AclEntry aclEntry = aeDAO.get(aeId);
		if(aclEntry == null){
			throw new CinnamonException("error.object.not.found");
		}
		Long pId = ParamParser.parseLong(cmd.get("permission_id"), "error.param.id");
		PermissionDAO pDao = daoFactory.getPermissionDAO(em);
		Permission permission = pDao.get(pId);
		if(permission == null){
			throw new CinnamonException("error.object.not.found");
		}
			
		AclEntryPermission aep =  new AclEntryPermission(aclEntry, permission);
		AclEntryPermissionDAO aepDao = daoFactory.getAclEntryPermissionDAO(em);
		aepDao.makePersistent(aep);
		Long aepId = aep.getId();
		log.debug("aepId: "+aepId);

		XmlResponse resp = new XmlResponse(res);
		resp.addTextNode("success", "success.add.permission");
		return resp;
	}
	
	/**
	 * Remove a Permission from an AclEntry.
     * @return XML response object
	 * {@code
	 * 	<success>success.remove.permission</success>
	 * }
	 * @param cmd Map with Key/Value-Pair<br>
	 * <ul>
	 *  <li>entry_id = Id of an AclEntry (integer)</li>
	 *  <li>permission_id = Id of Permission</li>
	 * </ul>
	 */
	@CinnamonMethod
	public Response removePermissionFromAclEntry(Map<String,String> cmd) {
		croakUnlessSuperuser("You need to be an admin to make acl-related changes.", 
				getUser());
				
		Long aeId = ParamParser.parseLong(cmd.get("entry_id"), "error.param.id");
		AclEntryDAO aeDAO = daoFactory.getAclEntryDAO(em);
		AclEntry aclEntry = aeDAO.get(aeId);
		if(aclEntry == null){
			throw new CinnamonException("error.object.not.found");
		}
			
		Long pId = ParamParser.parseLong(cmd.get("permission_id"), "error.param.id");
		PermissionDAO pDao = daoFactory.getPermissionDAO(em);
		Permission permission = pDao.get(pId);
		if(permission == null){
			throw new CinnamonException("error.object.not.found");
		}
			
		/*
		 * fetch AEP:
		 * a) load via Database
		 * b) iterate over aclEntry.getAEPs
		 */
		AclEntryPermission aep = null;
		for(AclEntryPermission a : aclEntry.getAePermissions()){
			if(a.getPermission().equals(permission)){
				aep = a;
				break;
			}
		}
	
		XmlResponse resp = new XmlResponse(res);
		if(aep != null){
			log.debug("found aep");
			AclEntryPermissionDAO aepDao = daoFactory.getAclEntryPermissionDAO(em);
			aclEntry.getAePermissions().remove(aep);
			permission.getAePermissions().remove(aep);
			aepDao.delete(aep);
			resp.addTextNode("success", "success.remove.permission");
		}
		else{
			/*
			 *  this can only happen if we did not find a matching AEP.
			 */
			throw new CinnamonException("fail.remove.permission");
		}
		return resp;
	}
}
