package server.helpers;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import server.*;
import server.dao.DAOFactory;
import server.dao.UserDAO;
import server.exceptions.CinnamonConfigurationException;
import server.extension.Initializer;
import server.global.ConfThreadLocal;
import server.interfaces.Repository;
import utils.HibernateSession;

import javax.persistence.EntityManager;
import javax.persistence.EntityTransaction;
import java.util.List;

/**
 * If a repository has not been initialized, install the absolute minimum to get started:
 * Default::
 * <ul>
 * <li>acl</li>
 * <li>object type</li>
 * <li>folder type</li>
 * <li>admin user</li>
 * <li>index config for system metadata</li>
 * </ul>
 * To determine if a system needs those types, the code checks if there are any user accounts in
 * the selected repository. A system without user accounts is considered to be uninitialized as it has
 * no objects.<br/>
 */
public class AutoInstaller {

    Logger log = LoggerFactory.getLogger(this.getClass());
    EntityManager em;
    static final DAOFactory daoFactory = DAOFactory.instance(DAOFactory.HIBERNATE);

    public Boolean isInitialized(Repository repository) {
        if(em == null){
            em = repository.getEntityManager();
        }
        UserDAO uDao = daoFactory.getUserDAO(em);
        List<User> users = uDao.list();
        return users.size() > 0;
    }

    public void initializeBasicSystem(Repository repository) {
        if(em == null){
            em = repository.getEntityManager();
        }
        Initializer initializer = new Initializer();
        initializer.setEm(em);
        initializer.setRepository(repository);
        EntityTransaction et = null;
        HibernateSession.setLocalEntityManager(em);
        try {
            et = em.getTransaction();
            et.begin();

            if (isInitialized(repository)) {
                log.warn("Cannot initialize an already initialized system.");
                return;
            }
            log.debug("Starting initialization.");

            initializer.createDefaultObjectTypes();
            initializer.createMetasetTypes();
            FolderType folderType = initializer.createDefaultFolderType();

            Acl acl = initializer.createDefaultAcl();
            Group superusers = initializer.createSuperuserAndAliasGroups();
            User admin = initializer.createAdminUserAndGroup(acl, superusers);
            initializer.createPermissions();
            log.debug(acl.toString());
            log.debug(admin.toString());
            log.debug(folderType.toString());
            Folder rootFolder = initializer.createRootFolder(acl, admin, folderType);

            initializer.createSystemFolders();
            initializer.createFormats();
            initializer.createSystemFolders();
            initializer.initializeIndex();
            initializer.addRelationResolvers();
            initializer.createLanguages();
            initializer.createConfigEntries();
            initializer.createRelationTypes();
            initializer.createRenderServerLifeCycle();
            initializer.initializeWorkflows(null);

            ConfThreadLocal config = ConfThreadLocal.getConf();
            String testQuery = String.format("cinnamon_config/repositories/repository[name='%s']/initialize-tests",
                    repository.getName());
            if(config.getField(testQuery, "false").equals("true")){
                initializer.createTestEnvironment(acl, admin, folderType, rootFolder);
            }

            log.debug("Initialization finished.");
            et.commit();
        } catch (Exception e) {
            log.debug("Failed to initialize system.", e);
            throw new CinnamonConfigurationException("Failed to initialize repository", e);
        } finally {
            if (et != null && et.isActive()) {
                et.rollback();
            }
        }
    }

}
