package server.extension.admin;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import server.CinnamonMethod;
import server.Format;
import server.dao.DAOFactory;
import server.dao.FormatDAO;
import server.dao.IndexTypeDAO;
import server.extension.BaseExtension;
import server.index.IndexType;
import server.interfaces.CommandRegistry;
import server.interfaces.Response;
import server.response.XmlResponse;
import utils.ParamParser;

import java.util.Map;

/**
 * This class provides methods for the cinnamon administrator to create and delete IndexTypes.
 * Note: Dandelion, the AdminTool, also does this
 * and more. But the methods here are more easily scripted via the safran client or other
 * libraries.
 *
 */
public class IndexTypeManager extends BaseExtension{
	
	
	static {
		/*
		 * Required so BaseExtension can set the API-Class.
		 */
		setExtensionClass(IndexTypeManager.class);
	}
	
	private Logger log = LoggerFactory.getLogger(this.getClass());
	
	public CommandRegistry registerApi(CommandRegistry cmdReg) {
		return findAndRegisterMethods(cmdReg, this);
	}
	
	static DAOFactory daoFactory = DAOFactory.instance(DAOFactory.HIBERNATE);

	/**
	 * Creates a new IndexType<br>
	 * @param cmd HTTP request parameter map:
	 * <ul>
	 * <li>command=createindextype</li>
	 * <li>name=Name of index type</li>
	 * <li>indexer_class= the name of the java class which is used to index items of this type</li>
	 * <li>data_type=the data type (for example: use STRING to create an index type for string items). Allowed values
     * are members of IndexType.DataType enum.
     * </li>
	 * <li>va_provider_class=Name of a class that acts as a value assistance provider (returning a list of
     * allowed values to display in a select list)</li>
	 * <li>ticket=session ticket</li>
	 * </ul>
     * @return XML-Response: the serialized indexType as XML
	 *
	 */
	@CinnamonMethod
	public Response createIndexType(Map<String,String> cmd) {
		croakUnlessSuperuser("error.must_be_admin", getUser());
        IndexType iType = new IndexType(cmd);
        IndexTypeDAO itDao = daoFactory.getIndexTypeDAO(em);
        itDao.makePersistent(iType);

		XmlResponse resp = new XmlResponse(res);
        iType.toXmlElement(resp.getDoc().getRootElement());
		return resp;
	}
	
	/**
	 * Deletes an indexType from the current repository.<br>
     * @param cmd HTTP request parameter map
	 * <ul>
	 * <li>command=deleteindextype</li>
	 * <li>id=id of the index type to be deleted</li>
	 * <li>ticket=session ticket</li>
	 * </ul>
     * @return XML-Response
	 * {@code
	 * 	<success>success.delete.indexType</success>
	 * }
	 */
	@CinnamonMethod
	public Response deleteIndexType(Map<String,String> cmd) {
		croakUnlessSuperuser("error.must_be_admin", getUser());
    	Long id = ParamParser.parseLong(cmd.get("id"), "error.param.id");

		log.debug( "Trying to delete IndexType " + id);
        IndexTypeDAO itDao = daoFactory.getIndexTypeDAO(em);
        itDao.delete(id);
		XmlResponse resp = new XmlResponse(res);
		resp.addTextNode("success", "success.delete.indexType");
		return  resp;
	}

}
