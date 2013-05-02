package workflow.transition;

import java.util.List;

import javax.persistence.EntityManager;

import org.dom4j.Document;
import org.dom4j.Node;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import server.Folder;
import server.ObjectType;
import server.User;
import server.dao.DAOFactory;
import server.dao.FolderDAO;
import server.dao.ObjectSystemDataDAO;
import server.dao.ObjectTypeDAO;
import server.dao.UserDAO;
import server.data.ObjectSystemData;
import server.exceptions.CinnamonConfigurationException;
import server.exceptions.CinnamonException;
import server.global.Constants;
import server.interfaces.Transition;
import utils.HibernateSession;

public abstract class BaseTransition implements Transition {
    protected Logger log = LoggerFactory.getLogger(this.getClass());
    DAOFactory daoFactory = DAOFactory.instance(DAOFactory.HIBERNATE);
    EntityManager em = HibernateSession.getLocalEntityManager();

    public User getUserFromMetadata(Document meta, String xpath) {
        Node userNode =  meta.selectSingleNode(xpath);
        if(userNode == null){
            throw new CinnamonException("error.param.undefined");
        }
        String userId = userNode.getText();
        UserDAO userDao = daoFactory.getUserDAO(em);
        User user = userDao.get(userId);
        if (user == null) {
            throw new CinnamonException("error.user.not_found");
        }
        return user;
    }

    public ObjectSystemData getOsdFromMetadata(Document meta, String xpath) {
        ObjectSystemDataDAO osdDao = daoFactory.getObjectSystemDataDAO(em);
        Node osdNode = meta.selectSingleNode(xpath);
        if(osdNode == null){
            throw new CinnamonException("error.param.undefined");
        }
        String id = osdNode.getText();
        log.debug("osd-id: " + id);
        ObjectSystemData osd = osdDao.get(id);
        if (osd == null) {
            throw new CinnamonException("error.object.not.found");
        }
        return osd;
    }

    public Folder getFolderFromMetadata(Document meta, String xpath) {
        FolderDAO folderDAO = daoFactory.getFolderDAO(em);
        Node folderNode = meta.selectSingleNode(xpath);
        if(folderNode == null){
            throw new CinnamonException("error.param.undefined");
        }
        String id = folderNode.getText();
        Folder folder = folderDAO.get(id);
        if (folder == null) {
            throw new CinnamonException("error.folder.not.found");
        }
        return folder;
    }
.
    // TODO: remove repositoryName parameter
    public ObjectSystemData createTask(String name, User owner, String repositoryName) {
        FolderDAO folderDao = daoFactory.getFolderDAO(em);
        Folder taskDefFolder = folderDao.findByPath(Constants.WORKFLOW_TASK_DEFINITION_PATH);
        Folder taskFolder = folderDao.findByPath(Constants.WORKFLOW_TASK_PATH);
        ObjectTypeDAO otDao = daoFactory.getObjectTypeDAO(em);
        ObjectType taskType = otDao.findByName(Constants.OBJTYPE_TASK);

        ObjectSystemDataDAO osdDao = daoFactory.getObjectSystemDataDAO(em);
        log.debug("taskdeffolder:" + taskDefFolder.getId() + " name: " + taskDefFolder.getName());

        List<ObjectSystemData> taskList = osdDao.findAllByParentAndName(taskDefFolder, name);
        if (taskList.isEmpty()) {
            throw new CinnamonConfigurationException(
                    String.format("Could not find a task '%s' for workflow transition", name));
        }
        if (taskList.size() > 1) {
            throw new CinnamonConfigurationException(
                    String.format("Found more than one task matching '%s'.", name));
        }
        ObjectSystemData taskDef = taskList.get(0);
        ObjectSystemData task = taskDef.createClone();
        task.setAcl(taskFolder.getAcl());
        task.setParent(taskFolder);
        task.setPredecessor(null);
        task.setOwner(owner);
        task.setProcstate(Constants.PROCSTATE_TASK_TODO);
        task.setType(taskType);
        osdDao.makePersistent(task);

        return task;
    }

    public void setNodeToIdValue(Document doc, String xpath, Long id) {
        Node node = doc.selectSingleNode(xpath);
        node.setText(String.valueOf(id));
    }

    public void setNodeText(Document doc, String xpath, String text) {
        Node node = doc.selectSingleNode(xpath);
        if(node == null){
            log.debug("document:\n{}", doc.asXML());
            log.debug("xpath: \n{}", xpath);
            throw new RuntimeException("error.xpath_node.not.found");
        }
        node.setText(text);
    }
}
