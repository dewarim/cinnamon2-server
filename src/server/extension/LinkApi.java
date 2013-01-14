package server.extension;

import org.dom4j.Document;
import org.dom4j.Element;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import server.*;
import server.dao.*;
import server.data.ObjectSystemData;
import server.data.Validator;
import server.exceptions.CinnamonException;
import server.global.PermissionName;
import server.index.Indexable;
import server.interfaces.CommandRegistry;
import server.interfaces.Response;
import server.lifecycle.LifeCycle;
import server.lifecycle.LifeCycleState;
import server.references.Link;
import server.references.LinkResolver;
import server.references.LinkService;
import server.references.LinkType;
import server.response.XmlResponse;
import utils.ParamParser;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

public class LinkApi extends BaseExtension {

    static {
        /*
           * Required so BaseExtension can set the API-Class.
           */
        setExtensionClass(LinkApi.class);
    }

    private Logger log = LoggerFactory.getLogger(this.getClass());

    public CommandRegistry registerApi(CommandRegistry cmdReg) {
        return findAndRegisterMethods(cmdReg, this);
    }

    static DAOFactory daoFactory = DAOFactory.instance(DAOFactory.HIBERNATE);

    /**
     * <h2>Parameters in HTTP Request</h2>
     * <ul>
     * <li>command=createlink</li>
     * <li>id= the id of the target object or folder which to which the link will point</li>
     * <li>acl_id= the acl of the link</li>
     * <li>owner_id= the owner of the link</li>
     * <li>type= one of FOLDER or OBJECT, to determine the type of linked object.</li>
     * <li>[resolver]= how the link should be resolved: defaults to FIXED, may be LATEST_HEAD for type=OBJECT</li>
     * <li>parent_id= the id of the folder with which to associate the new link object</li>
     * <li>ticket=session ticket</li>
     * </ul>
     * <p/>
     * <h2>Needed permissions</h2>
     * <ul>
     * <li>BROWSE_OBJECT (or: BROWSE_FOLDER)</li>
     * </ul>
     *
     * @param cmd a Map of HTTP request parameters
     * @return a CinnamonException on failure or a Response object with
     *         the following XML content:
     *         <pre>
     *         {@code
     *          <link>
     *              <object>
     *                  ... (object data)
     *                  <reference>
     *                      <linkId>id of the link object</linkId>
     *                      <id>link target id</id>
     *                      <parentId></parentId>
     *                      <aclId></aclId>
     *                      <ownerId></ownerId>
     *                      <resolver>FIXED</resolver>
     *                      <type>OBJECT</type>
     *                  </reference>
     *              </object>
     *
     *              or, in case of type=FOLDER:
     *              <folder>
     *                  ... (folder data)
     *                  <reference>
     *                      <linkId>id of the link object</linkId>
     *                      <id>link target id</id>
     *                      <parentId></parentId>
     *                      <aclId></aclId>
     *                      <ownerId></ownerId>
     *                      <resolver>FIXED</resolver>
     *                      <type>FOLDER</type>
     *                  </reference>
     *              </folder>
     *          </link>
     *         }
     *         </pre>
     */
    @CinnamonMethod(checkTrigger = "true")
    public Response createLink(Map<String, String> cmd) {
        String id = cmd.get("id");
        if (id == null) {
            throw new CinnamonException("error.param.id");
        }

        AclDAO aDao = daoFactory.getAclDAO(em);
        FolderDAO fDao = daoFactory.getFolderDAO(em);
        ObjectSystemDataDAO oDao = daoFactory.getObjectSystemDataDAO(em);
        UserDAO uDao = daoFactory.getUserDAO(em);

        Acl acl = aDao.get(ParamParser.parseLong(cmd.get("acl_id"), "error.param.acl_id"));
        Folder parent = fDao.get(ParamParser.parseLong(cmd.get("parent_id"), "error.param.parent_id"));
        User owner = uDao.get(ParamParser.parseLong(cmd.get("owner_id"), "error.param.owner_id"));
        ObjectSystemData osd = null;
        Folder folder = null;
        String typeName = cmd.get("type");
        Validator validator = new Validator(getUser());
        LinkResolver resolver;
        if (cmd.containsKey("resolver")) {
            resolver = LinkResolver.valueOf(cmd.get("resolver"));
        } else {
            resolver = LinkResolver.FIXED;
        }

        XmlResponse resp = new XmlResponse(res);
        Link link;
        LinkService linkService = new LinkService();
        LinkType linkType = LinkType.valueOf(typeName);
        if (linkType == LinkType.FOLDER) {
            folder = fDao.get(id);
            if (folder == null) {
                throw new CinnamonException("error.param.id");
            }
            validator.validatePermission(folder.getAcl(), PermissionName.BROWSE_FOLDER);
            link = linkService.createLink(folder, parent, acl, owner, resolver);
        } else {
            osd = oDao.get(id);
            if (osd == null) {
                throw new CinnamonException("error.param.id");
            }
            validator.validatePermission(osd.getAcl(), PermissionName.BROWSE_OBJECT);
            link = linkService.createLink(osd, parent, acl, owner, resolver);
        }

        em.persist(link);
        linkService.renderLinkWithinTarget(link, resp.getDoc());
        log.debug("result of createLink:\n" + resp.getDoc().asXML());
        return resp;
    }

    /**
     * <h2>Parameters in HTTP Request</h2>
     * Most parameters are optional [...], but you must include at least one of them,
     * otherwise this method will do nothing.
     * <ul>
     * <li>command=updatelink</li>
     * <li>link_id= if of the link object </li>
     * <li>[acl_id]= new acl id for the link</li>
     * <li>[owner_id]= new owner for the link</li>
     * <li>[resolver]= how the link should be resolved: defaults to FIXED, may be LATEST_HEAD for type=OBJECT</li>
     * <li>[parent_id]= the id of the folder with which to associate the new link object</li>
     * <li>[object_id]= the id of another version of the linked object (gives error if object.root is different).</li>
     * <li>ticket=session ticket</li>
     * </ul>
     * <h2>Needed permissions</h2>
     * <ul>
     *   <li>BROWSE_OBJECT (or: BROWSE_FOLDER)</li>
     *   <li>WRITE_OBJECT_SYS_METADATA</li>
     *   <li>SET_ACL for changes to the ACL.</li>
     * </ul>
     * @param cmd a Map of HTTP request parameters
     * @return a CinnamonException on failure or a Response object with
     *         the following XML content:
     *         <pre>
     *         {@code
     *          <link>
     *              <object>
     *                  ... (object data)
     *                  <reference>
     *                      <linkId>id of the link object</linkId>
     *                      <id>link target id</id>
     *                      <parentId></parentId>
     *                      <aclId></aclId>
     *                      <ownerId></ownerId>
     *                      <resolver>FIXED</resolver>
     *                      <type>FOLDER</type>
     *                  </reference>
     *              </object>
     *
     *              or, in case of type=FOLDER:
     *              <folder>
     *                  ... (folder data)
     *                  <reference>
     *                      <linkId>id of the link object</linkId>
     *                      <id>link target id</id>
     *                      <id></id>
     *                      <parentId></parentId>
     *                      <aclId></aclId>
     *                      <ownerId></ownerId>
     *                      <resolver>FIXED</resolver>
     *                      <type>FOLDER</type>
     *                  </reference>
     *              </folder>
     *          </link>
     *         }
     *         </pre>
     *         <p/>
     */
    @CinnamonMethod(checkTrigger = "true")
    public Response updateLink(Map<String, String> cmd) {
        Long linkId = ParamParser.parseLong(cmd.get("link_id"), "error.param.link_id");
        LinkService linkService = new LinkService();
        Link link = em.find(Link.class, linkId);
        if (link == null) {
            throw new CinnamonException("error.object.not.found");
        }

        Validator validator = new Validator(getUser());
        validator.validatePermission(link.getAcl(), PermissionName.WRITE_OBJECT_SYS_METADATA);
        if(cmd.containsKey("acl_id")){
            validator.validatePermission(link.getAcl(), PermissionName.SET_ACL);
        }
        
        link = linkService.updateLink(link, cmd); 
      
        XmlResponse resp = new XmlResponse(res);
        linkService.renderLinkWithinTarget(link, resp.getDoc());
        log.debug("result of updateLink:\n" + resp.getDoc().asXML());
        return resp;
    }
    
    
    /**
     * <h2>Parameters in HTTP Request</h2>
     * Most parameters are optional [...], but you must include at least one of them,
     * otherwise this method will do nothing.
     * <ul>
     * <li>command=getlink</li>
     * <li>link_id= id of the link object </li>
     * <li>ticket=session ticket</li>
     * </ul>
     * <h2>Needed permissions</h2>
     * <ul>
     *   <li>BROWSE_OBJECT (or: BROWSE_FOLDER)</li>
     * </ul>
     * @param cmd a Map of HTTP request parameters
     * @return a CinnamonException on failure or a Response object with
     *         the following XML content:
     *         <pre>
     *         {@code
     *          <link>
     *              <object>
     *                  ... (object data)
     *                  <reference>
     *                      <linkId>id of the link object</linkId>
     *                      <id>link target id</id>
     *                      <parentId></parentId>
     *                      <aclId></aclId>
     *                      <ownerId></ownerId>
     *                      <resolver>FIXED</resolver>
     *                      <type>FOLDER</type>
     *                  </reference>
     *              </object>
     *
     *              or, in case of type=FOLDER:
     *              <folder>
     *                  ... (folder data)
     *                  <reference>
     *                      <linkId>id of the link object</linkId>
     *                      <id>link target id</id>
     *                      <id></id>
     *                      <parentId></parentId>
     *                      <aclId></aclId>
     *                      <ownerId></ownerId>
     *                      <resolver>FIXED</resolver>
     *                      <type>FOLDER</type>
     *                  </reference>
     *              </folder>
     *          </link>
     *         }
     *         </pre>
     *         <p/>
     */
    @CinnamonMethod(checkTrigger = "true")
    public Response getLink(Map<String, String> cmd) {
        Long linkId = ParamParser.parseLong(cmd.get("link_id"), "error.param.link_id");
        LinkService linkService = new LinkService();
        Link link = em.find(Link.class, linkId);
        if (link == null) {
            throw new CinnamonException("error.object.not.found");
        }

        Validator validator = new Validator(getUser());
        if (link.getType() == LinkType.FOLDER) {
            validator.validatePermission(link.getAcl(), PermissionName.BROWSE_FOLDER);
            validator.validatePermission(link.getFolder().getAcl(), PermissionName.BROWSE_FOLDER);
        } else {
            validator.validatePermission(link.getAcl(), PermissionName.BROWSE_OBJECT);
            validator.validatePermission(link.getOsd().getAcl(), PermissionName.BROWSE_OBJECT);
        }

        XmlResponse resp = new XmlResponse(res);
        linkService.renderLinkWithinTarget(link, resp.getDoc());
        log.debug("result of getLink:\n" + resp.getDoc().asXML());
        return resp;
    }

    /**
     * <h2>Parameters in HTTP Request</h2>
     * <ul>
     * <li>command=deletelink</li>
     * <li>link_id= id of the link object </li>
     * <li>ticket=session ticket</li>
     * </ul>
     * <h2>Needed permissions</h2>
     * <ul>
     * <li>DELETE_FOLDER or DELETE_OBJECT on the link's acl.</li>
     * </ul>
     *
     * @param cmd a Map of HTTP request parameters
     * @return a CinnamonException on failure or a Response object with
     *         the following XML content:
     *         <pre>
     *         {@code
     *         <success>success.delete.link</success>
     *         }
     *         </pre>
     */
    @CinnamonMethod(checkTrigger = "true")
    public Response deleteLink(Map<String, String> cmd) {
        Long linkId = ParamParser.parseLong(cmd.get("link_id"), "error.param.link_id");
        Link link = em.find(Link.class, linkId);
        if (link == null) {
            throw new CinnamonException("error.object.not.found");
        }

        Validator validator = new Validator(getUser());
        if(link.getType().equals(LinkType.FOLDER)){
            validator.validatePermission(link.getAcl(), PermissionName.DELETE_FOLDER);
        }
        else{
            validator.validatePermission(link.getAcl(), PermissionName.DELETE_OBJECT);
        }
        
        em.remove(link);
        
        XmlResponse resp = new XmlResponse(res);
        resp.getDoc().addElement("success").addText("success.delete.link");

        log.debug("result of deleteLink:\n" + resp.getDoc().asXML());
        return resp;
    }


}