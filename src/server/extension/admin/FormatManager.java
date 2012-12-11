package server.extension.admin;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import server.CinnamonMethod;
import server.Format;
import server.dao.DAOFactory;
import server.dao.FormatDAO;
import server.extension.BaseExtension;
import server.interfaces.CommandRegistry;
import server.interfaces.Response;
import server.response.XmlResponse;
import utils.ParamParser;

/**
 * This class provides methods for the cinnamon administrator to create and delete Formats.
 * Note: Dandelion, the AdminTool, also does this
 * and more. But then, the methods here are more easily scripted via the safran client 
 * (which is useful for API tests that need an additional test user etc).
 * 
 */
public class FormatManager extends BaseExtension{
	
	
	static {
		/*
		 * Required so BaseExtension can set the API-Class.
		 */
		setExtensionClass(FormatManager.class);
	}
	
	private Logger log = LoggerFactory.getLogger(this.getClass());
	
	public CommandRegistry registerApi(CommandRegistry cmdReg) {
		return findAndRegisterMethods(cmdReg, this);
	}
	
	static DAOFactory daoFactory = DAOFactory.instance(DAOFactory.HIBERNATE);

	/**
	 * Creates a new format from which the client can deduce which program to call for
	 * further content handling. (Eg, an xml2pdf-converter plugin)<br>
	 * @param cmd HTTP request parameter map:
	 * <ul>
	 * <li>command=createformat</li>
	 * <li>name=Name of format, eg: xml</li>
	 * <li>extension=File extension (Windows), eg: xml</li>
	 * <li>contenttype=mime-type</li>
	 * <li>description=description of Format, eg: XML File</li>
	 * <li>ticket=session ticket</li>
	 * </ul>
     * @return XML-Response:
	 * {@code
	 * 	<formatId>$id</formatId>
	 * }
	 * 
	 */
	@CinnamonMethod
	public Response createFormat(Map<String,String> cmd) {
		croakUnlessSuperuser("error.must_be_admin", getUser());
		
		Format format = new Format(cmd);
		FormatDAO formatDao = daoFactory.getFormatDAO(em);
		formatDao.makePersistent(format);
		
		XmlResponse resp = new XmlResponse(res);
		resp.addTextNode("formatId", String.valueOf(format.getId()) );
		return resp;
	}
	
	/**
	 * Deletes a format from the current repository.<br>
	 * TODO: Who may delete formats? Currently: Superadmins
     * @param cmd HTTP request parameter map
	 * <ul>
	 * <li>command=deleteformat</li>
	 * <li>format_id=id of the format to be deleted</li>
	 * <li>ticket=session ticket</li>
	 * </ul>
     * @return XML-Response
	 * {@code
	 * 	<success>success.delete.format</success>
	 * }
	 */
	@CinnamonMethod
	public Response deleteFormat(Map<String,String> cmd) {
		croakUnlessSuperuser("error.must_be_admin", getUser());
    	Long id = ParamParser.parseLong(cmd.get("format_id"), "error.param.format_id");

		log.debug( "Trying to delete Format " + id);
		FormatDAO formatDAO = daoFactory.getFormatDAO(em);
		formatDAO.delete(id);
		XmlResponse resp = new XmlResponse(res);
		resp.addTextNode("success", "success.delete.format");
		return  resp;
	}
}
