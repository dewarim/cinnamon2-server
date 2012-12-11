package server.extension;

import org.dom4j.Element;
import org.dom4j.Node;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import server.CinnamonMethod;
import server.ObjectType;
import server.User;
import server.dao.*;
import server.data.ObjectSystemData;
import server.data.Validator;
import server.exceptions.CinnamonConfigurationException;
import server.exceptions.CinnamonException;
import server.global.Conf;
import server.global.Constants;
import server.interfaces.CommandRegistry;
import server.interfaces.Response;
import server.lifecycle.LifeCycle;
import server.response.XmlResponse;
import utils.ParamParser;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

public class RenderServerConnector extends BaseExtension {

    static {
        /*
           * Required so BaseExtension can set the API-Class.
           */
        setExtensionClass(RenderServerConnector.class);
    }

    private Logger log = LoggerFactory.getLogger(this.getClass());

    public CommandRegistry registerApi(CommandRegistry cmdReg) {
        return findAndRegisterMethods(cmdReg, this);
    }

    static DAOFactory daoFactory = DAOFactory.instance(DAOFactory.HIBERNATE);

    /**
     * The startRenderTask creates a new OSD of the object type render_task in the repository
     * in the specified folder.
     * This task object is read by the render server and updated with status updates by the render
     * process (which are written to custom metadata /meta/metaset=render_output/messages/). It is up
     * to the client to interpret the messages.
     * The overall status of the render task can be determined by looking at the procstate attribute:<br>
     * While the task is waiting for the render server to pick it up, its procstate is set to "waiting".
     * While the task is running, its procstate is set to "running".
     * After the task is finished, the task's procstate attribute is set to "finished".
     * If the task should fail, the task's procstate is set to "failed".
     * <h2>Required permissions</h2>
     * CREATE_OBJECT
     *
     * @param cmd a Map of HTTP request parameters containing:<br>
     * <ul>
     *   <li>command=startrendertask</li>
     *   <li>[name]=optional name of the task, defaults to "RenderTask"</li>
     *   <li>ticket=session ticket</li>
     *   <li>parentid = id of the folder where the task-object will be created</li>
     *   <li>metadata = xml to use as the metadata metaset=render_input field. It must contain
     * at least the element <pre>{@code <renderTaskName>}</pre> which holds the name of the
     * render task that will be performed. It should contain the sourceId element
     * to specify the id of the source content object to be rendered.<br>
     *     Example for metadata content:<br>
     *  <pre>{@code
     *  <metaset type="render_input"><sourceId>542</sourceId><renderTaskName>foo</renderTaskName></metaset>
     *  }</pre>
     *   </li>
     * </ul>
     * @return a CinnamonException if the object cannot be instantiated for any reason,
     *         or a Response object with the following XML content:
     * <pre>
     * {@code
     *   <startRenderTask>
     *     <taskObjectId>123</taskObjectId>
     *     <success>success.startRenderTask</success>
     *  </startRenderTask>
     * }
     * </pre>
     */
    @CinnamonMethod(checkTrigger = "true")
    public Response startRenderTask(Map<String, Object> cmd) {
        // create object and validate permission
        User user = getUser();
        ObjectSystemData osd = new ObjectSystemData(cmd, user, false);
        (new Validator(user)).validateCreate(osd.getParent());

        String renderInput;
        if (cmd.containsKey("metadata")) {
            Node meta = ParamParser.parseXml((String) cmd.get("metadata"), "error.param.metadata");
            renderInput = meta.asXML();
        }
        else {
            renderInput = "";
        }
        String metasetStr = "<meta>"+renderInput+"<metaset type=\"render_output\"></metaset></meta>";
        Node metaset = ParamParser.parseXml(metasetStr, null);
        osd.setMetadata(metaset.asXML());
        LifeCycleDAO lcDao = daoFactory.getLifeCycleDAO(em);
        LifeCycle lc = lcDao.findByName(Constants.RENDER_SERVER_LIFECYCLE);
        if(lc == null){
            throw new CinnamonConfigurationException(Constants.RENDER_SERVER_LIFECYCLE+" lifecycle was not found.");
        }
        if(lc.getDefaultState() == null){
            throw new CinnamonConfigurationException(Constants.RENDER_SERVER_LIFECYCLE+" lifecycle is not configured correctly. Needs defaultState.");
        }
        osd.setState(lc.getDefaultState());
        osd.setProcstate(Constants.RENDERSERVER_RENDER_TASK_NEW);

        if(cmd.containsKey("name")){
            osd.setName( ((String) cmd.get("name")).trim() );
        }
        else{
            osd.setName("RenderTask");
        }

        ObjectTypeDAO otDao = daoFactory.getObjectTypeDAO(em);
        ObjectType renderTaskType = otDao.findByName(Constants.OBJECT_TYPE_RENDER_TASK);
        if (renderTaskType == null) {
            throw new CinnamonConfigurationException("Could not find required render task object type.");
        }
        osd.setType(renderTaskType);
        ObjectSystemDataDAO oDao = daoFactory.getObjectSystemDataDAO(em);
        oDao.makePersistent(osd);

		repository.getLuceneBridge().addObjectToIndex(osd);

        // create response
        XmlResponse resp = new XmlResponse(res);
        Element root = resp.getDoc().addElement("startRenderTask");
        root.addElement("taskObjectId").addText(String.valueOf(osd.getId()));
        root.addElement("success").addText("success.startRenderTask");
        return resp;
    }

}