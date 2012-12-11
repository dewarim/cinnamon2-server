package server.exporter;

import org.dom4j.Element;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import server.*;
import server.dao.*;
import server.data.ObjectSystemData;
import server.data.Validator;
import server.exceptions.CinnamonConfigurationException;
import server.extension.BaseExtension;
import server.global.Constants;
import server.interfaces.CommandRegistry;
import server.interfaces.Response;
import server.response.FileResponse;
import server.response.XmlResponse;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class Exporter extends BaseExtension {

    static {
        /*
           * Required so BaseExtension can set the API-Class.
           */
        setExtensionClass(Exporter.class);
    }

    private Logger log = LoggerFactory.getLogger(this.getClass());

    public CommandRegistry registerApi(CommandRegistry cmdReg) {
        return findAndRegisterMethods(cmdReg, this);
    }

    static DAOFactory daoFactory = DAOFactory.instance(DAOFactory.HIBERNATE);

    /**
     * Export an object and recursively all child objects it depends on as a zip archive.
     * @param cmd a Map of HTTP request parameters containing:<br>
     *            <ul>
     *            <li>command=export</li>
     *            <li>checkout=[true|false] flag to indicate if the item will be checked out or simply packaged for download. Default: false</li>
     *            <li>ticket=session ticket</li>
     *            <li>id = id of the object that will be exported</li>
     *            </ul>
     * @return a CinnamonException if the object cannot be exported for any reason,
     *         or a Response object which sends a zip file to the client for download.
     */
    @CinnamonMethod
    public Response exportObject(Map<String, String> cmd) {
        // create object and validate permission
        User user = getUser();
        ObjectSystemDataDAO oDao = daoFactory.getObjectSystemDataDAO(em);
        ObjectSystemData osd = oDao.get(cmd.get("id"));
        (new Validator(user)).validateGetContent(osd);

        Set<ObjectSystemData> objects = new HashSet<ObjectSystemData>();
        findExportTargets(osd, objects);

        // TODO: validate getContent for each.

        // create zip

        // create response
        FileResponse resp = new FileResponse(res);
        return resp;
    }

    void findExportTargets(ObjectSystemData osd, Set<ObjectSystemData> targets) {
        if (targets.add(osd)) {
            RelationDAO rDao = daoFactory.getRelationDAO(em);
            RelationTypeDAO rtDao = daoFactory.getRelationTypeDAO(em);
            RelationType rType = rtDao.findByName(Constants.RELATION_TYPE_CHILD);
            List<Relation> childRelations = rDao.findAllByLeftAndType(osd, rType);
            for (Relation relation : childRelations) {
                Boolean isNew = targets.add(relation.getRight());
                if (isNew) {
                    findExportTargets(relation.getRight(), targets);
                }
            }
        }
    }

}