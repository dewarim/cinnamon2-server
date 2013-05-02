package server.extension;

import java.util.HashMap;
import java.util.Map;
import java.util.List;

import org.dom4j.Element;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import server.*;
import server.dao.*;
import server.data.ObjectSystemData;
import server.exceptions.CinnamonConfigurationException;
import server.global.ConfThreadLocal;
import server.global.Constants;
import server.i18n.Language;
import server.i18n.Message;
import server.i18n.UiLanguage;
import server.index.CinnamonIndexInitializer;
import server.index.IndexGroup;
import server.index.IndexItem;
import server.index.IndexType;
import server.index.LuceneBridge;
import server.interfaces.CommandRegistry;
import server.interfaces.IRelationResolver;
import server.interfaces.Response;
import server.lifecycle.LifeCycle;
import server.lifecycle.LifeCycleState;
import server.lifecycle.state.ChangeAclState;
import server.lifecycle.state.DemoAuthoringState;
import server.lifecycle.state.DemoPublishedState;
import server.lifecycle.state.DemoReviewState;
import server.resolver.FixedRelationResolver;
import server.resolver.LatestBranchResolver;
import server.resolver.LatestHeadResolver;
import server.response.XmlResponse;
import server.trigger.ChangeTrigger;
import server.trigger.ChangeTriggerType;
import server.trigger.test.TestTrigger;
import server.trigger.impl.RelationChangeTrigger;
import utils.HibernateSession;

public class Initializer extends BaseExtension {

    static {
        /*
           * Required so BaseExtension can set the API-Class.
           */
        setExtensionClass(Initializer.class);
    }

    private Logger log = LoggerFactory.getLogger(this.getClass());

    public CommandRegistry registerApi(CommandRegistry cmdReg) {
        return findAndRegisterMethods(cmdReg, this);
    }

    static DAOFactory daoFactory = DAOFactory.instance(DAOFactory.HIBERNATE);

    /**
     * The initializeDatabase command requires an empty database, which it will
     * fill with all the basic objects required to create users. Foremost is the
     * creation of an admin account with default password.
     * <p/>
     * <h2>Parameters in HTTP Request</h2>
     * <ul>
     * <li>command=initializedatabase</li>
     * <li>repository=the repository you wish to initialize</li>
     * </ul>
     *
     * @param cmd the HTTP-request parameters as a Map[String,String].
     * @return &lt;result&gt;"Initialization completed."&lt;/result&gt;
     */
    @CinnamonMethod
    public Response initializeDatabase(Map<String, String> cmd) {
        /*
           * Check if the database is truly empty.
           * We assume that will be the case if no users can be found.
           */
        UserDAO userDAO = daoFactory.getUserDAO(em);
        List<User> users = userDAO.list();
        if (!users.isEmpty()) {
            throw new CinnamonConfigurationException("Cannot initialize database: it already contains user accounts!");
        }
        HibernateSession.setLocalEntityManager(em);
        createMetasetTypes();

        // create default objtype:
        createDefaultObjectTypes();

        // create default FolderType:
        FolderType folderType = createDefaultFolderType();

        // create default acl:
        Acl acl = createDefaultAcl();

        // create _superusers group:
        Group superusers = createSuperuserAndAliasGroups();
        User admin = createAdminUserAndGroup(acl, superusers);

        // create basic permissions
        createPermissions();

        // create root folder:
        Folder rootFolder = createRootFolder(acl, admin, folderType);

        // create system folder:
        createSystemFolders();

        // create basic formats
        createFormats();

        createSystemFolders();

        // create a basic indextype
        initializeIndex();

        // Add default RelationResolvers:
        addRelationResolvers();

        // create the basic languages for UI and objects:
        createLanguages();

        // create ConfigEntry for translations:
        createConfigEntries();

        createTestEnvironment(acl, admin, folderType, rootFolder);

        // initialize RelationTypes
        createRelationTypes();

        // create basic RenderServer life cycle.
        createRenderServerLifeCycle();

        // clear the index:
        clearLuceneIndex();

        log.debug("creating xmlResponse with res:" + res);
        XmlResponse resp = new XmlResponse(res);
        Element root = resp.getDoc().addElement("initializeDatabase");
        Element result = root.addElement("result");
        result.addText("Initialization completed.");
        return resp;
    }

    /**
     * Create test elements for:
     * <ul>
     * <li>TransformationTest</li>
     * <li>ChangeTriggerTest</li>
     * <li>IndexTest</li>
     * </ul>
     *
     * @param acl        the default ACL
     * @param admin      admin user
     * @param folderType default folder type
     * @param rootFolder root folder
     */
    public void createTestEnvironment(Acl acl, User admin, FolderType folderType, Folder rootFolder) {
        // create test transformation
        FormatDAO fDao = daoFactory.getFormatDAO(em);
        Format xml = fDao.findByName("xml");
        Format pdf = fDao.findByName("pdf");
        // commented out pdfTestTransformer as it
//        Transformer trans = new Transformer("pdfTestTransformer", "Transform xml to pdf",
//                XhtmlToPdfTransformer.class, xml, pdf);
//        TransformerDAO transDao = daoFactory.getTransformerDAO(em);
//        transDao.makePersistent(trans);

        // create a ChangeTriggerType for testing:
        ChangeTriggerTypeDAO cttd = daoFactory.getChangeTriggerTypeDAO(em);
        ChangeTriggerType ctt = new ChangeTriggerType("TestChangeTriggerType",
                "This trigger type loads a test class", TestTrigger.class);
        cttd.makePersistent(ctt);

        // create a ChangeTrigger
        ChangeTriggerDAO ctDao = daoFactory.getChangeTriggerDAO(em);
        ChangeTrigger ct = new ChangeTrigger("setcontent", ctt, 1, true, true, true);
        ctDao.makePersistent(ct);
        ChangeTrigger ct2 = new ChangeTrigger("setcontent", ctt, 2, false, false, true);
        ctDao.makePersistent(ct2); //2nd trigger to test active/non-active triggers.
        ChangeTrigger setMetaTrigger = new ChangeTrigger("setmeta", ctt, 3, true, false, true);
        ctDao.makePersistent(setMetaTrigger);

        // create a message-object for testing:
        UiLanguageDAO uiLangDao = daoFactory.getUiLanguageDAO(em);
        MessageDAO messageDao = daoFactory.getMessageDAO(em);
        UiLanguage lang = uiLangDao.findByIsoCode("und");
        Message message = new Message("_default_acl", lang, "Default ACL");
        messageDao.makePersistent(message);

        // create more IndexItems for testing purposes.
        /*
         * This item is needed as long as there is no API to create IndexItems.
         */
        IndexGroupDAO igDao = daoFactory.getIndexGroupDAO(em);
        IndexGroup ig = igDao.findByName(Constants.INDEX_GROUP_DEFAULT);
        log.debug("create IndexTypes");
        IndexTypeDAO itDao = daoFactory.getIndexTypeDAO(em);
        IndexType type = itDao.findByName("xpath.string_indexer");
        IndexItemDAO iiDao = daoFactory.getIndexItemDAO(em);
        IndexItem ii = new IndexItem("test.index.name", "//name",
                "true()", "content", type,
                true, "<vaParams/>", true, ig, true, true, true);
        iiDao.makePersistent(ii);
        ii = new IndexItem("test.index.description", "//description",
                "true()", "content", type,
                true, "<vaParams/>", false, ig, true, true, true);
        iiDao.makePersistent(ii);
        ii = new IndexItem("test.index.attribute", "//test/@attrib",
                "true()", "content", type,
                true, "<vaParams/>", false, ig, true, true, true);
        iiDao.makePersistent(ii);
        ii = new IndexItem("test.index.search_condition", "//name",
                "string(/meta/metaset[@type='test']/name) = 'Smaug'", "conditional_content", type,
                true, "<vaParams/>", false, ig, true, true, true);
        iiDao.makePersistent(ii);
        type = itDao.findByName("xpath.decimal_indexer");
        ii = new IndexItem("test.index.decimal", "//test/dec",
                "true()", "content", type,
                true, "<vaParams/>", false, ig, true, true, true);
        iiDao.makePersistent(ii);
        em.flush();
        LuceneBridge lucene = repository.getLuceneBridge();
        lucene.setIndexItemList(iiDao.list());
    }

    /**
     * The initializeWorkflows command requires an initialized database and
     * you need to be connected to the repository for which you want to add
     * the basic workflow objects and prerequisites.
     * <p/>
     * <h2>Parameters in HTTP Request</h2>
     * <ul>
     * <li>command=initializeworkflows</li>
     * </ul>
     *
     * @param cmd the HTTP-request parameters as a Map[String,String].
     * @return &lt;result&gt;"Initialization completed."&lt;/result&gt;
     */
    @CinnamonMethod
    public Response initializeWorkflows(Map<String, String> cmd) {


        // create workflow relation types:
        // from workflow_template to start task:
        RelationTypeDAO rtDao = daoFactory.getRelationTypeDAO(em);
        RelationType checkStatus = rtDao.findByName(Constants.RELATION_TYPE_WORKFLOW_TO_START_TASK);
        if (checkStatus != null) {
            throw new CinnamonConfigurationException("Workflows have already been initialized.");
        }

        String[] relTypes = {
                Constants.RELATION_TYPE_WORKFLOW_TO_START_TASK,
                Constants.RELATION_TYPE_WORKFLOW_TO_TASK,
                Constants.RELATION_TYPE_WORKFLOW_TO_DEADLINE_TASK
        };
        RelationResolverDAO rdd = daoFactory.getRelationResolverDAO(em);
        RelationResolver fixedResolver = rdd.findByName("FixedRelationResolver");
        for (String relName : relTypes) {

            RelationType rType = new RelationType(relName, relName + ".description",
                    true, true,
                    fixedResolver, fixedResolver);
            rtDao.makePersistent(rType);
        }

        // create IndexItems for Workflows:
        IndexGroupDAO igDao = daoFactory.getIndexGroupDAO(em);
        IndexGroup iGroup = igDao.findByName(Constants.INDEX_GROUP_DEFAULT);
        IndexTypeDAO itDao = daoFactory.getIndexTypeDAO(em);
        IndexType type = itDao.findByName("xpath.boolean_indexer");
        IndexItemDAO iiDao = daoFactory.getIndexItemDAO(em);
        IndexItem ii = new IndexItem("index.workflow.active_workflow", "/meta/metaset[@type='workflow_template']/active_workflow",
                "true()", "active_workflow", type, false, "<vaParams/>", true, iGroup, false, true, false);
        iiDao.makePersistent(ii);
        IndexType timeIndexer = itDao.findByName("xpath.date_time_indexer");
        ii = new IndexItem("index.workflow.deadline", "/meta/metaset[@type='task_definition' or @type='workflow_template']/deadline",
                "true()", "workflow_deadline", timeIndexer,
                false, "<vaParams/>", true, iGroup, false, true, false);
        iiDao.makePersistent(ii);
        // update IndexItem cache:
        repository.getLuceneBridge().setIndexItemList(iiDao.list());

        // create demo workflow: => done by Test.

        log.debug("creating xmlResponse with res:" + res);
        XmlResponse resp = new XmlResponse(res);
        Element root = resp.getDoc().addElement("initializeWorkflow");
        Element result = root.addElement("result");
        result.addText("Initialization completed.");
        return resp;
    }

    /**
     * The initializeLifecycles command requires an initialized database and
     * you need to be connected to the repository for which you want to add
     * the basic lifecycle objects and prerequisites needed for testing.
     * <p/>
     * <h2>Parameters in HTTP Request</h2>
     * <ul>
     * <li>command=initializelifecycles</li>
     * </ul>
     *
     * @param cmd the HTTP-request parameters as a Map[String,String].
     * @return &lt;result&gt;"Initialization completed."&lt;/result&gt;
     */
    @CinnamonMethod
    public Response initializeLifecycles(Map<String, String> cmd) {
        /*
           * Check if the database contains any lifecycles.
           */
        LifeCycleDAO lcDao = daoFactory.getLifeCycleDAO(em);
        List<LifeCycle> cycles = lcDao.list();
        if (!cycles.isEmpty()) {
            throw new CinnamonConfigurationException(
                    "Cannot initialize lifecycles: database was already initialized.");
        }

        // create demo lifecycle
        LifeCycle lifeCycle = new LifeCycle("Test Lifecycle", null);
        lcDao.makePersistent(lifeCycle);

        // create LifeCycleState objects
        LifeCycleStateDAO lcsDAo = daoFactory.getLifeCycleStateDAO(em);
        LifeCycleState authoring = new LifeCycleState("authoring", DemoAuthoringState.class, "<params/>", lifeCycle);
        lcsDAo.makePersistent(authoring);
        LifeCycleState review = new LifeCycleState("review", DemoReviewState.class, "<params/>", lifeCycle);
        lcsDAo.makePersistent(review);
        LifeCycleState published = new LifeCycleState("published", DemoPublishedState.class, "<params/>", lifeCycle);
        lcsDAo.makePersistent(published);
        lifeCycle.setDefaultState(authoring);

        // Create objects required forChangeAclState class test.
        LifeCycle aclLc = new LifeCycle("TestAclLc", null);
        lcDao.makePersistent(aclLc);
        LifeCycleState defaultAclState = new LifeCycleState("defaultAclState", ChangeAclState.class,
                "<params><aclName>" + Constants.ACL_DEFAULT + "</aclName></params>", aclLc);
        lcsDAo.makePersistent(defaultAclState);
        AclDAO aclDao = daoFactory.getAclDAO(em);
        Acl otherAcl = new Acl("_another_acl", "Test ACL for LifeCycleState 'TestAclLc'");
        aclDao.makePersistent(otherAcl);
        LifeCycleState otherState = new LifeCycleState("otherAclState", ChangeAclState.class,
                "<params><aclName>_another_acl</aclName></params>", aclLc);
        lcsDAo.makePersistent(otherState);
        aclLc.setDefaultState(defaultAclState);

        log.debug("creating xmlResponse with res:" + res);
        XmlResponse resp = new XmlResponse(res);
        Element root = resp.getDoc().addElement("initializeLifecycles");
        Element result = root.addElement("result");
        result.addText("Initialization completed.");
        return resp;
    }

    public Acl createDefaultAcl() {
        log.debug("create default acl");
        Acl acl = new Acl("_default_acl", "Default ACL");
        AclDAO aclDAO = daoFactory.getAclDAO(em);
        aclDAO.makePersistent(acl);
        return acl;
    }

    public FolderType createDefaultFolderType() {
        log.debug("create default folderType");
        FolderType folderType = new FolderType(Constants.FOLDER_TYPE_DEFAULT, "Default Folder Type");
        FolderTypeDAO folderTypeDAO = daoFactory.getFolderTypeDAO(em);
        folderTypeDAO.makePersistent(folderType);
        return folderType;
    }

    /**
     * Create the default object type and other object types.
     *
     * @return the default ObjectType
     */
    public ObjectType createDefaultObjectTypes() {
        log.debug("create default object types");

        Map<String, String> oTypes = new HashMap<String, String>();
        oTypes.put(Constants.OBJECT_TYPE_RENDER_TASK, "Render Task Type");
        oTypes.put(Constants.OBJTYPE_CART, "Cart");
        oTypes.put(Constants.OBJTYPE_CONFIG, "Config");
        oTypes.put(Constants.OBJTYPE_RENDITION, "Rendition");
        oTypes.put(Constants.OBJTYPE_SEARCH, "Search");
        oTypes.put(Constants.OBJTYPE_SUPPORTING_DOCUMENT, "Supporting document");
        oTypes.put(Constants.OBJTYPE_DOCUMENT, "Document");
        oTypes.put(Constants.OBJTYPE_TRANSLATION_TASK, "Translation task");
        oTypes.put(Constants.OBJTYPE_IMAGE, "Image");
        oTypes.put(Constants.OBJTYPE_NOTIFICATION, Constants.OBJTYPE_NOTIFICATION);
        oTypes.put(Constants.OBJTYPE_WORKFLOW, Constants.OBJTYPE_WORKFLOW + ".workflow");
        oTypes.put(Constants.OBJTYPE_WORKFLOW_TEMPLATE, Constants.OBJTYPE_WORKFLOW_TEMPLATE + ".description");
        oTypes.put(Constants.OBJTYPE_TASK, Constants.OBJTYPE_TASK + ".description");
        oTypes.put(Constants.OBJTYPE_TASK_DEFINITION, Constants.OBJTYPE_TASK_DEFINITION + ".description");

        ObjectTypeDAO otDao = daoFactory.getObjectTypeDAO(em);
        for (String name : oTypes.keySet()) {
            ObjectType oType = new ObjectType(name, oTypes.get(name));
            otDao.makePersistent(oType);
        }
        ObjectType defaultObjectType = new ObjectType(Constants.OBJTYPE_DEFAULT, "Default Object Type");
        otDao.makePersistent(defaultObjectType);
        return defaultObjectType;
    }

    /**
     * @return the superusers group.
     */
    public Group createSuperuserAndAliasGroups() {
        GroupDAO gDao = daoFactory.getGroupDAO(em);
        Group superusers = new Group(Constants.GROUP_SUPERUSERS, "Superusers", false, null);
        gDao.makePersistent(superusers);

        // create default alias groups:
        for (String alias : Group.defaultGroups) {
            Group defaultGroup = new Group(alias, alias + ".description", false, null);
            gDao.makePersistent(defaultGroup);
        }
        return superusers;
    }

    public User createAdminUserAndGroup(Acl defaultAcl, Group superusers) {
        // create admin user:
        log.debug("create admin user");
        User admin = new User("admin", "admin", "Administrator", "Cinnamon Administrator");
        admin.setSudoer(true);
        admin.setEmail(ConfThreadLocal.getConf().getField("cinnamon_config/system-administrator", null));
        UserDAO uDao = daoFactory.getUserDAO(em);
        uDao.makePersistent(admin);
        String groupName = "_" + admin.getId() + "_" + admin.getName();

        // create personal group for admin:
        Group adminGroup = new Group(groupName, "Admin's personal group", true, null);
        GroupDAO groupDAO = daoFactory.getGroupDAO(em);
        groupDAO.makePersistent(adminGroup);

        GroupUser gu = new GroupUser(admin, adminGroup);
        em.persist(gu);

//        Long groupUserId = groupDAO.addToUser(admin.getId(), adminGroup.getId());

        // personal group should be connected to the default_acl:
        log.debug(defaultAcl.toString());
        AclEntry defaultAndAdmin = new AclEntry(defaultAcl, adminGroup);
        em.persist(defaultAndAdmin);

//        AclEntry aclEntry = groupDAO.addToAcl(defaultAcl.getId(), adminGroup.getId());

        GroupUser superGroupUser = new GroupUser(admin, superusers);
        em.persist(superGroupUser);
//        Long gu = gDao.addToUser(admin.getId(), superusers.getId());

        return admin;
    }

    public void createPermissions() {
        PermissionDAO permissionDao = daoFactory.getPermissionDAO(em);
        for (String name : Permission.defaultPermissions) {
            Permission p = new Permission(name, name + ".description");
            permissionDao.makePersistent(p);
        }
    }

    public Folder createRootFolder(Acl acl, User owner, FolderType type) {
        Folder rootFolder = new Folder(Constants.ROOT_FOLDER_NAME, "", acl, null, owner, type);
        FolderDAO folderDao = daoFactory.getFolderDAO(em);
        folderDao.makePersistent(rootFolder);
        rootFolder.setParent(rootFolder);
        return rootFolder;
    }

    public void createSystemFolders() {
        FolderDAO fDao = daoFactory.getFolderDAO(em);
        String[] paths = {
                "/system/applications",
                "/system/custom",
                "/system/transient",
                "/templates",
                "/system/config",
                "/system/transient/render_tasks",
                "/system/transient/translation_tasks",
                "/system/transient/workflow/notifications",
                "/system/users/admin/config",
                "/system/users/admin/home",
                "/system/users/admin/carts",
                "/system/users/admin/searches",
                "/system/workflows/templates",
                "/system/workflows/task_definitions",
                "/system/workflows/tasks",
        };
        for (String path : paths) {
            fDao.findAllByPath(path, true, null);
        }
    }

    public void createFormats() {
        Format xmlFormat = new Format("xml", "xml", "application/xml", "XML Document");
        FormatDAO formatDAO = daoFactory.getFormatDAO(em);
        formatDAO.makePersistent(xmlFormat);
        Format pdfFormat = new Format("pdf", "pdf", "application/pdf", "PDF File");
        formatDAO.makePersistent(pdfFormat);
        Format jpeg = new Format("jpeg", "jpeg", "image/jpeg", "JPEG Image");
        formatDAO.makePersistent(jpeg);
        Format png = new Format("png", "png", "image/png", "PNG Image");
        formatDAO.makePersistent(png);
        Format gif = new Format("gif", "gif", "image/gif", "GIF Image");
        formatDAO.makePersistent(gif);
        Format eps = new Format("eps", "eps", "image/eps", "Encapsulated Postscript");
        formatDAO.makePersistent(eps);
        Format tiff = new Format("tiff", "tiff", "image/tiff", "TIFF Image");
        formatDAO.makePersistent(tiff);
        Format txt = new Format("txt", "txt", "text/plain", "Text");
        formatDAO.makePersistent(txt);
        Format odt = new Format("odt", "odt", "application/x-vnd.oasis.opendocument.text", "Open Document Format");
        formatDAO.makePersistent(odt);
        Format doc = new Format("doc", "doc", "application/msword", "MS-Word Document");
        formatDAO.makePersistent(doc);
        Format fm = new Format("fm", "fm", "application/fm", "FrameMaker");
        formatDAO.makePersistent(fm);
        Format clp = new Format("clp", "clp", "application/clp", "Clipboard clip");
        formatDAO.makePersistent(clp);
        Format html = new Format("html", "html", "text/html", "HTML");
        formatDAO.makePersistent(html);
        Format dtd = new Format("dtd", "dtd", "application/dtd", "Document Type Definition");
        formatDAO.makePersistent(dtd);
        Format css = new Format("css", "css", "application/css", "Cascading Style Sheet");
        formatDAO.makePersistent(css);
        Format zip = new Format("zip", "zip", "application/zip", "ZIP File");
        formatDAO.makePersistent(zip);
        Format xsd = new Format("xsd", "xsd", "application/xsd", "XML Schema");
        formatDAO.makePersistent(xsd);
        Format csv = new Format("csv", "csv", "text/csv", "comma-separated-values");
        formatDAO.makePersistent(csv);
        Format cdr = new Format("cdr", "cdr", "application/cdr", "CorelDraw");
        formatDAO.makePersistent(cdr);
        Format dita = new Format("dita", "dita", "text/xml", "DITA topic");
        formatDAO.makePersistent(dita);
        Format ditamap = new Format("ditamap", "ditamap", "text/xml", "DITA map");
        formatDAO.makePersistent(ditamap);
        Format ods = new Format("ods", "ods", "application/vnd.oasis.opendocument.spreadsheet", "OpenDocument spreadsheet");
        formatDAO.makePersistent(ods);
        Format odp = new Format("odp", "odp", "application/vnd.oasis.opendocument.presentation", "OpenDocument presentation");
        formatDAO.makePersistent(odp);
        Format odg = new Format("odg", "odg", "application/vnd.oasis.opendocument.graphics", "OpenDocument drawing");
        formatDAO.makePersistent(odg);
        Format odc = new Format("odc", "odc", "application/vnd.oasis.opendocument.chart", "OpenDocument chart");
        formatDAO.makePersistent(odc);
        Format odf = new Format("odf", "odf", "application/vnd.oasis.opendocument.formula", "OpenDocument formula");
        formatDAO.makePersistent(odf);
        Format odi = new Format("odi", "odi", "application/vnd.oasis.opendocument.image", "OpenDocument image");
        formatDAO.makePersistent(odi);
        Format odm = new Format("odm", "odm", "application/vnd.oasis.opendocument.text-master", "OpenDocument master document");
        formatDAO.makePersistent(odm);
        Format odb = new Format("odb", "odb", "application/vnd.oasis.opendocument.database", "OpenDocument database");
        formatDAO.makePersistent(odb);
        Format ott = new Format("ott", "ott", "application/vnd.oasis.opendocument.text-template", "OpenDocument text template");
        formatDAO.makePersistent(ott);
        Format ots = new Format("ots", "ots", "application/vnd.oasis.opendocument.spreadsheet-template", "OpenDocument spreadsheet template");
        formatDAO.makePersistent(ots);
        Format otp = new Format("otp", "otp", "application/vnd.oasis.opendocument.presentation-template", "OpenDocument presentation template");
        formatDAO.makePersistent(otp);
        Format otg = new Format("otg", "otg", "application/vnd.oasis.opendocument.graphics-template", "OpenDocument drawing template");
        formatDAO.makePersistent(otg);
        Format otc = new Format("otc", "otc", "application/vnd.oasis.opendocument.chart-template", "OpenDocument chart template");
        formatDAO.makePersistent(otc);
        Format otf = new Format("otf", "otf", "application/vnd.oasis.opendocument.formula-template", "OpenDocument formula template");
        formatDAO.makePersistent(otf);
        Format oti = new Format("oti", "oti", "application/vnd.oasis.opendocument.image-template", "OpenDocument image template");
        formatDAO.makePersistent(oti);
        Format oth = new Format("oth", "oth", "application/vnd.oasis.opendocument.text-web", "OpenDocument web page template");
        formatDAO.makePersistent(oth);
        Format xls = new Format("xls", "xls", "application/vnd.ms-excel", "Microsoft Excel");
        formatDAO.makePersistent(xls);
        Format pps = new Format("pps", "pps", "application/vnd.ms-powerpoint", "Microsoft Powerpoint");
        formatDAO.makePersistent(pps);
        Format ppt = new Format("ppt", "ppt", "application/vnd.ms-powerpoint", "Microsoft Powerpoint");
        formatDAO.makePersistent(ppt);
        Format xlsx = new Format("xlsx", "xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "Microsoft Excel 2007");
        formatDAO.makePersistent(xlsx);
        Format pptx = new Format("pptx", "pptx", "application/vnd.openxmlformats-officedocument.presentationml.presentation", "Microsoft Powerpoint 2007");
        formatDAO.makePersistent(pptx);
        Format docx = new Format("docx", "docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document", "Microsoft Word 2007");
        formatDAO.makePersistent(docx);
    }

    public void initializeIndex() {
        log.debug("create CinnamonIndexInitializer");
        CinnamonIndexInitializer cii = new CinnamonIndexInitializer(em);
        IndexGroup ig = cii.createDefaultIndexGroup();
        cii.initializeIndexTypes();
        cii.createFolderSysMetaItems();
        cii.createOSDSysMetaItems();
    }

    public void addRelationResolvers() {
        RelationResolverDAO rdd = daoFactory.getRelationResolverDAO(em);
        Class[] resolvers = {
                FixedRelationResolver.class,
                LatestHeadResolver.class,
                LatestBranchResolver.class
        };
        for (Class<IRelationResolver> resolverClass : resolvers) {
            RelationResolver resolver = new RelationResolver("", resolverClass.getName(), resolverClass.getSimpleName());
            rdd.makePersistent(resolver);
        }
        // create ChangeTrigger for RelationResolver:
        ChangeTriggerDAO ctDao = daoFactory.getChangeTriggerDAO(em);
        ChangeTriggerTypeDAO cttd = daoFactory.getChangeTriggerTypeDAO(em);
        ChangeTriggerType relationTriggerType = new ChangeTriggerType("RelationChangeTriggerType",
                "Required trigger type for relation change trigger", RelationChangeTrigger.class);
        cttd.makePersistent(relationTriggerType);
        String[] cmdList = {"setcontent", "setmeta", "setsysmeta", "version", "delete"};
        for (String command : cmdList) {
            ChangeTrigger relationTrigger = new ChangeTrigger(command, relationTriggerType, 100, true, true, true);
            ctDao.makePersistent(relationTrigger);
        }
    }

    public void createLanguages() {
        UiLanguageDAO uiLangDao = daoFactory.getUiLanguageDAO(em);
        LanguageDAO langDao = daoFactory.getLanguageDAO(em);
        String[] languages = {"mul", "und", "zxx"};
        for (String isoCode : languages) {
            UiLanguage uiLang = new UiLanguage(isoCode);
            uiLangDao.makePersistent(uiLang);
            Language lang = new Language(isoCode);
            langDao.makePersistent(lang);
        }
    }

    public void createConfigEntries() {
        log.debug("Create ConfigEntry for translation.config");
        ConfigEntryDAO ceDao = daoFactory.getConfigEntryDAO(em);
        ConfigEntry configEntry = new ConfigEntry("translation.config", "<config><aclForTranslatedObjects>" +
                Constants.ACL_DEFAULT + "</aclForTranslatedObjects></config>");
        ceDao.makePersistent(configEntry);
    }

    public void clearLuceneIndex() {
        // this action may fail if the index does not exist.
        try {
            LuceneBridge lucene = repository.getLuceneBridge();
            lucene.removeClassFromIndex(ObjectSystemData.class);
            lucene.removeClassFromIndex(Folder.class);
        } catch (Exception e) {
            log.debug("clearing index failed. This may happen when either the permissions of" +
                    "the index folders are wrong or if no index exists yet. Message:\n", e);
        }
    }

    public void createRelationTypes() {
        RelationTypeDAO rtDao = daoFactory.getRelationTypeDAO(em);
        RelationResolverDAO rsDao = daoFactory.getRelationResolverDAO(em);
        RelationResolver resolver = rsDao.findByName(Constants.RELATION_RESOLVER_FIXED);

        RelationType childContent = new RelationType(Constants.RELATION_TYPE_CHILD,
                Constants.RELATION_TYPE_CHILD + ".description",
                false, true, resolver, resolver);
        childContent.setCloneOnLeftCopy(true);
        rtDao.makePersistent(childContent);

        RelationType rendition = new RelationType(Constants.RELATION_TYPE_RENDITION,
                Constants.RELATION_TYPE_RENDITION + ".description",
                true, false, resolver, resolver);
        rtDao.makePersistent(rendition);

        RelationType translationSource = new RelationType(Constants.RELATION_TYPE_TRANSLATION_SOURCE,
                Constants.RELATION_TYPE_TRANSLATION_SOURCE + ".description",
                true, false, resolver, resolver);
        rtDao.makePersistent(translationSource);

        RelationType translationRoot = new RelationType(Constants.RELATION_TYPE_TRANSLATION_ROOT,
                Constants.RELATION_TYPE_TRANSLATION_ROOT + ".description",
                true, false, resolver, resolver);
        rtDao.makePersistent(translationRoot);

        RelationType childNoContent = new RelationType(Constants.RELATION_TYPE_CHILD_NO_CONTENT,
                Constants.RELATION_TYPE_CHILD_NO_CONTENT + ".description",
                false, true, resolver, resolver);
        childNoContent.setCloneOnLeftCopy(true);
        rtDao.makePersistent(childNoContent);

        RelationType translationSourceList = new RelationType(Constants.RELATION_TYPE_TRANSLATION_SOURCE_LIST,
                Constants.RELATION_TYPE_TRANSLATION_SOURCE_LIST + ".description",
                false, true, resolver, resolver);
        rtDao.makePersistent(translationSourceList);

        RelationType translationTargetList = new RelationType(Constants.RELATION_TYPE_TRANSLATION_TARGET_LIST,
                Constants.RELATION_TYPE_TRANSLATION_TARGET_LIST + ".description",
                false, true, resolver, resolver);
        rtDao.makePersistent(translationTargetList);
    }

    public void createRenderServerLifeCycle() {
        // create Lifecycle.
        LifeCycleDAO lcDao = daoFactory.getLifeCycleDAO(em);
        LifeCycle renderLc = new LifeCycle(Constants.RENDER_SERVER_LIFECYCLE, null);
        lcDao.makePersistent(renderLc);

        // create LifeCycleStates
        String[] lcList = {
                Constants.RENDERSERVER_RENDER_TASK_FAILED,
                Constants.RENDERSERVER_RENDER_TASK_FINISHED,
                Constants.RENDERSERVER_RENDER_TASK_RENDERING,
                Constants.RENDERSERVER_RENDER_TASK_NEW
        };
        LifeCycleStateDAO lcsDao = daoFactory.getLifeCycleStateDAO(em);
        for (String lcsName : lcList) {
            LifeCycleState lcs = new LifeCycleState(lcsName, server.lifecycle.state.NopState.class, null, renderLc, null);
            lcsDao.makePersistent(lcs);
            if (lcsName.equals(Constants.RENDERSERVER_RENDER_TASK_NEW)) {
                renderLc.setDefaultState(lcs);
            }
        }
    }

    public void createMetasetTypes(){
        log.debug("create metaset types");
        MetasetTypeDAO mtDao = daoFactory.getMetasetTypeDAO(em);
        String[] metasetNames = {"search", "cart", "translation_extension", "render_input", "render_output", 
                "test", "tika", "task_definition", "transition", "workflow_template", 
                Constants.METASET_NOTIFICATION};
        for(String name : metasetNames){
            MetasetType metasetType = new MetasetType(name, name, null);
            mtDao.makePersistent(metasetType);
        }
    }

}
