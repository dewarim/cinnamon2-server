package server.extension.admin;

import java.util.Map;

import org.dom4j.Element;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import server.CinnamonMethod;
import server.FolderType;
import server.dao.DAOFactory;
import server.dao.FolderTypeDAO;
import server.extension.BaseExtension;
import server.interfaces.CommandRegistry;
import server.interfaces.Response;
import server.response.XmlResponse;
import utils.ParamParser;

/**
 * This class provides methods for the cinnamon administrator to create and delete FolderTypes.
 * Note: Dandelion, the AdminTool, also does this
 * and more. But then, the methods here are more easily scripted via the safran client 
 * (which is useful for API tests that need an additional test user etc).
 * 
 */
public class FolderTypeManager extends BaseExtension{
	
	
	static {
		/*
		 * Required so BaseExtension can set the API-Class.
		 */
		setExtensionClass(FolderTypeManager.class);
	}
	
	private Logger log = LoggerFactory.getLogger(this.getClass());
	
	public CommandRegistry registerApi(CommandRegistry cmdReg) {
		return findAndRegisterMethods(cmdReg, this);
	}
	
	static DAOFactory daoFactory = DAOFactory.instance(DAOFactory.HIBERNATE);

	/**
	 * The createFolderType command must be issued by a member of the <em>superusers</em>group.
     * @param cmd HTTP request parameters as map:
	 * <ul>
	 * <li>command=createfoldertype</li>
	 * <li>name=name of foldertype</li>
	 * <li>description=description the new foldertype</li>
	 * <li>ticket=session ticket</li>
	 * </ul>
	 * @return XML representation of the new folderType.
	 */
	@CinnamonMethod
	public Response createFolderType(Map<String,String> cmd) {
		croakUnlessSuperuser("error.must_be_admin", getUser());

		FolderType folderType = new FolderType(cmd);   	
		FolderTypeDAO FolderTypeDAO = daoFactory.getFolderTypeDAO(em);
		FolderTypeDAO.makePersistent(folderType);
		XmlResponse resp = new XmlResponse(res);
		Element root = resp.getDoc().addElement("folderTypes");
		root.add(FolderType.asElement("folderType", folderType));
		return resp; 
	}
	
	/**
	 * Delete a FolderType.
     * @return XML response:
	 * {@code
	 * 	<success>success.delete.folder_type</success>
	 *  }
	 * 
	 * @param cmd HTTP request parameters as Map:<br>
	 * 	<ul>
	 * 	<li>id = Id of the FolderType</li>
	 * 	</ul>
	 */
	@CinnamonMethod
	public Response deleteFolderType(Map<String,String> cmd) {
		croakUnlessSuperuser("error.must_be_admin", getUser());
		Long folderTypeID = ParamParser.parseLong(cmd.get("id"), "error.param.id");

		log.debug( "Trying to delete FolderType " + folderTypeID);

	    FolderTypeDAO folderTypeDAO = daoFactory.getFolderTypeDAO(em);
	    folderTypeDAO.delete(folderTypeID);
	    log.debug( "Deleted FolderType.");
	    XmlResponse resp = new XmlResponse(res);
		resp.addTextNode("success","success.delete.folder_type");
	    return resp;
	}
}
