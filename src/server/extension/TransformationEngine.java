package server.extension;

import java.io.File;
import java.util.Map;
import java.util.List;

import org.dom4j.Document;
import org.dom4j.Element;

import server.CinnamonMethod;
import server.dao.DAOFactory;
import server.dao.ObjectSystemDataDAO;
import server.dao.TransformerDAO;
import server.data.ObjectSystemData;
import server.data.Validator;
import server.exceptions.CinnamonException;
import server.global.PermissionName;
import server.interfaces.CommandRegistry;
import server.interfaces.Response;
import server.response.FileResponse;
import server.response.XmlResponse;
import server.transformation.ITransformer;
import server.transformation.Transformer;

public class TransformationEngine extends BaseExtension{

	static {
		/*
		 * Required so BaseExtension can set the API-Class.
		 */
		setExtensionClass(TransformationEngine.class);
	}
	
	public CommandRegistry registerApi(CommandRegistry cmdReg) {
		return findAndRegisterMethods(cmdReg, this);
	}
	
	static DAOFactory daoFactory = DAOFactory.instance(DAOFactory.HIBERNATE);
	
	/**
	 * Transform the content of an object.

	 * Note: a transformation may also change this object's metadata.
	 * 	<h2>Needed permissions</h2>
	 * <ul>
	 *  <li>WRITE_OBJECT_CONTENT</li>
	 *  <li>WRITE_OBJECT_CUSTOM_METADATA</li>
	 *  <li>WRITE_OBJECT_SYS_METADATA</li>
	 * </ul> 
	 * @param cmd Map with key/value pairs:
	 * <ul>
	 *  <li>id = the id of the OSD</li>
	 *  <li>transformer_id = the id of the transformer object which will perform
	 * 	the transformation</li>
	 *  <li>transformation_params = additional parameters for the transformation class</li>   
	 * </ul>
	 * @return a XmlResponse with a "success" or an "error" node.
	 */
	@CinnamonMethod
	public Response transformObject(Map<String, String> cmd){
		ObjectSystemData source = getSourceOSD(cmd);
		String params = cmd.get("transformation_params");
		ITransformer itrans = getITransformer(cmd);
		validateTransformationPermission(source);
		itrans.transformOSD(source, params, repository.getName() );
		return new XmlResponse(res, "<success>transform.success</success>");
	}

	/**
	 * Transform the content of an OSD and send it to the client.
	 * <h2>Needed permissions</h2>
	 * <ul>
	 *  <li>WRITE_OBJECT_CONTENT</li>
	 *  <li>WRITE_OBJECT_CUSTOM_METADATA</li>
	 *  <li>WRITE_OBJECT_SYS_METADATA</li>
	 * </ul> 
	 * @param cmd
	 * Map with key/value pairs:
	 * <ul>
	 * 	<li>id = the id of the OSD</li>
	 * 	<li>transformer_id = the id of the transformer object which will perform
	 * the transformation</li>
	 * 	<li>transformation_params = additional parameters for the transformation class</li>   
	 * </ul>
	 * @return a FileResponse or in case of an error an XmlResponse
	 */
	@CinnamonMethod
	public Response transformObjectToFile(Map<String, String> cmd){
		ObjectSystemData source = getSourceOSD(cmd);	
		String params = cmd.get("transformation_params");
		ITransformer itrans = getITransformer(cmd);
		validateTransformationPermission(source);
		File transformationResult = itrans.transformToFile(source, params, repository.getName());
		return new FileResponse(getRes(),
				transformationResult.getAbsolutePath(), transformationResult.length(), transformationResult.getName());
	}
	
	/**
	 * Load the source OSD for a transformation
	 * @param cmd Map with key/value pair "id" = source OSD 
	 * @return the OSD to be transformed
	 */
	ObjectSystemData getSourceOSD(Map<String, String> cmd){
		ObjectSystemDataDAO osdDao = daoFactory.getObjectSystemDataDAO(em);
		ObjectSystemData osd = osdDao.get(cmd.get("id"));
		if(osd == null){
			throw new CinnamonException("error.object.not.found");
		}
		return osd;
	}
	
	void validateTransformationPermission(ObjectSystemData osd){
		(new Validator(getUser())).validatePermissions(osd,
				PermissionName.WRITE_OBJECT_CONTENT,
				PermissionName.WRITE_OBJECT_CUSTOM_METADATA,
				PermissionName.WRITE_OBJECT_SYS_METADATA);
	}
	
	ITransformer getITransformer(Map<String, String> cmd){
		TransformerDAO transDao = daoFactory.getTransformerDAO(em);
		Transformer transformer = transDao.get(cmd.get("transformer_id"));
		ITransformer itrans;
		if(transformer == null){
			throw new CinnamonException("error.transformer.not_found");
		}
		try {
			Class<? extends ITransformer> transformerClass = transformer.getTransformerClass();
			itrans = transformerClass.newInstance();
			itrans.setTransformer(transformer);
		} catch (InstantiationException e) {
			throw new CinnamonException("error.instantiating.class", e);
		} catch (IllegalAccessException e) {
			throw new CinnamonException("error.accessing.class", e);
		}
		return itrans;
	}
	
	/**
	 * Request a list of all registered transformers from the server.
	 * The response has the following format:
	 * 
	 * @param cmd HashMap of HTTP-Request parameters and values
	 * @return an XmlResponse containing the list of transformers in the format
	 * <pre>
	 * {@code <transformers>
	 * 	<transformer>...</transformer>
	 * </transformers>}
	 * </pre>
	 */
	@CinnamonMethod
	public Response listTransformers(Map<String, String> cmd){
		TransformerDAO transDao = daoFactory.getTransformerDAO(em);
		List<Transformer> transformers = transDao.list();
		
		XmlResponse resp = new XmlResponse(res);
		Document doc = resp.getDoc();
		Element root = doc.addElement("transformers");
		for(Transformer t : transformers){
			root.add(Transformer.asElement("transformer", t));
		}
		return resp;
	}
	
}
