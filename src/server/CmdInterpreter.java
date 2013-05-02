// cinnamon - the Open Enterprise CMS project
// Copyright (C) 2007-2009 Horner GmbH (http://www.horner-project.eu)
// 
// This library is free software; you can redistribute it and/or
// modify it under the terms of the GNU Lesser General Public
// License as published by the Free Software Foundation; either
// version 2.1 of the License, or (at your option) any later version.
// 
// This library is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
// Lesser General Public License for more details.
// 
// You should have received a copy of the GNU Lesser General Public
// License along with this library; if not, write to the Free Software
// Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301  USA
// (or visit: http://www.gnu.org/licenses/lgpl.html)

package server;

import java.io.*;
import java.util.*;

import javax.persistence.EntityManager;
import javax.persistence.EntityTransaction;
import javax.persistence.NoResultException;
import javax.servlet.http.HttpServletResponse;

import org.apache.lucene.search.ScoreDoc;
import org.apache.velocity.Template;
import org.apache.velocity.VelocityContext;
import org.dom4j.Document;
import org.dom4j.Element;
import org.dom4j.Node;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import server.account.TemplateProvider;
import server.audit.AuditService;
import server.audit.LogEvent;
import server.dao.*;
import server.data.*;
import server.exceptions.CinnamonConfigurationException;
import server.exceptions.CinnamonException;
import server.global.Conf;
import server.global.ConfThreadLocal;
import server.global.PermissionName;
import server.helpers.MetasetService;
import server.i18n.Language;
import server.i18n.LocalMessage;
import server.i18n.Message;
import server.i18n.UiLanguage;
import server.index.*;
import server.interfaces.*;
import server.references.Link;
import server.references.LinkService;
import server.references.LinkType;
import server.response.FileResponse;
import server.response.HtmlResponse;
import server.response.TextResponse;
import server.response.XmlResponse;
import server.global.Constants;
import server.tika.TikaParser;
import utils.ContentReader;
import utils.FileKeeper;
import utils.HibernateSession;
import utils.ParamParser;
import utils.security.HashMaker;
import org.slf4j.MDC;

public class CmdInterpreter extends ApiClass implements ApiProvider {

    private Logger log = LoggerFactory.getLogger(this.getClass());
    private static DAOFactory daoFactory = DAOFactory.instance(DAOFactory.HIBERNATE);
    /**
     * Allowed values for the "parameter" field for setSysMeta.
     */
    public final static List<String> parameters = Arrays.asList("parentid", "name", "owner", "procstate", "acl_id", "objtype", "appname", "language_id");

    // commonly used attributes:
    private User user;
    private Repository repository;
    private EntityManager em;
    private EntityTransaction etx;
    private HttpServletResponse res;
    private Conf conf = ConfThreadLocal.getConf();

    @SuppressWarnings("unused")
    private Map<String, Repository> repositories;

    public CommandRegistry registerApi(CommandRegistry cmdReg) {
        Class<?> classList[] = {server.CmdInterpreter.class};
        Map<String, Class<?>> methods = findMethods(classList);
        return registerMethods(cmdReg, methods);
    }

    public CmdInterpreter() {
        log.debug("In constructor CmdInterpreter.");
    }

    public void interpret(Map<String, Object> cmd, HttpServletResponse res,
                          Map<String, Repository> repositories)
            throws IOException {

        log.debug("interpreting...");
        this.repositories = repositories;
//        for (String s : repositories.keySet()) {
//            log.debug("repository: " + s);
//        }
        this.res = res;

        String command = (String) cmd.get("command");
        if (command == null || command.length() == 0) {
            command = "(none given)";
        }

        String ticket;
        Response response = null;
        User user = null;
        Long sessionId = null;
        String username = null;
        Long userId = null;
        try {
            // setup helper objects and attributes
            if (cmd.containsKey("ticket") && !cmd.containsValue("forksession")) {

                ticket = (String) cmd.get("ticket");
                if (ticket.getBytes("UTF8").length < 38) { // the UUID part of the ticket is 36 characters long + '@' + at least one character
                    throw new CinnamonConfigurationException("ticket is too short");
                }
                String repo = ticket.split("@")[1];
                if (!repositories.containsKey(repo)) {
                    throw new CinnamonConfigurationException(String.format("invalid repository '%s'", repo));
                }
                repository = repositories.get(repo);

                setEm(repository.getHibernateSession().getEntityManager());
                HibernateSession.setLocalEntityManager(getEm()); // set this thread's EntityManager
                log.debug("initialize Session");
                
                LocalRepository.setRepository(repository);
                EntityTransaction et = em.getTransaction();
                et.begin();
                Session session = null;
                try {
                    session = Session.initSession(em, ticket, repository.getName(), command);
                    if(session != null){ // session may be null if it was not found and command=disconnect
                        sessionId = session.getId();
                        userId = session.getUser().getId();
                        username = session.getUser().getName();
                        log.debug("found user: " + userId);
                    }
                    et.commit();
                } catch (Exception e) {
                    et.rollback();
                    throw e;
                } finally {
                    if (session != null) {
                        log.debug("initializeLocalMessage");
                        MessageDAO mDao = daoFactory.getMessageDAO(em);
                        LocalMessage.initializeLocalMessage(mDao, session.getLanguage());
                    }
                }

                if (conf.getUseSessionLogging() && session != null) {
                    activateSessionLogging(ticket, repository, sessionId, username);
                }
            } else if (cmd.containsKey("repository") || cmd.containsValue("forksession")) {
                String repo = (String) cmd.get("repository");
                if (repo == null && cmd.containsValue("forksession")) {
                    // should be forkSession:
                    String fsTicket = (String) cmd.get("ticket");
                    repo = fsTicket.split("@")[1];
                }
                log.debug("request targets repository: '" + repo + "'");
                repository = repositories.get(repo);
                if (repository == null) {
                    log.debug("unknown repository " + repo);
                    throw new CinnamonException("error.unknown.repository", repo);
                }
                HibernateSession hs = repository.getHibernateSession();
                setEm(hs.getEntityManager());
                HibernateSession.setLocalEntityManager(getEm()); // set this thread's EntityManager
            } else {
                log.debug("cmd contains neither ticket nor repository string.");
                throw new CinnamonConfigurationException("cmd contains neither ticket nor repository string.");
            }

            log.debug("Repository: " + repository.getName());
            setRepository(repository);
            HibernateSession.setLocalRepositoryName(repository.getName());

            log.debug("------------------------------------");
            log.debug("Invoking: *** " + command + " ***");
            for (String key : cmd.keySet()) {
                log.debug(String.format("%s='%s'", key, cmd.get(key).toString()));
            }
            etx = getEm().getTransaction();
            etx.begin();

            if (userId != null) {
                log.debug("load session's user");
                // load the session's user in the current EntityTransaction
                UserDAO uDao = daoFactory.getUserDAO(em);
                user = uDao.get(userId);
            }
            response = repository.getCommandRegistry().invoke(command, cmd, res, user, repository);
            etx.commit();
            if(repository.getAuditConnection() != null){
                log.debug("commit audit log");
                repository.getAuditConnection().commit();                        
            }            
            
            log.debug("Update Lucene Index");
            EntityTransaction indexUpdate = em.getTransaction();
            indexUpdate.begin();
            Map<Indexable, IndexAction> updatedObjects = LocalRepository.getUpdatedObjects();
            for(Indexable indexable : updatedObjects.keySet()){
                log.debug("Working on indexable #"+indexable.myId());
                LuceneBridge luceneBridge = repository.getLuceneBridge();
                switch(updatedObjects.get(indexable)){
                    case ADD: luceneBridge.addObjectToIndex(indexable, false);break;
                    case UPDATE: luceneBridge.updateObjectInIndex(indexable);break;
                    case REMOVE: luceneBridge.removeObjectFromIndex(indexable);break;
                }
            }           
            indexUpdate.commit();

            log.debug("closing em after invoke.");
            getEm().close();

            /*
             * So far, everything has gone according to plan - now it's
             * time to delete the files which are to be removed.              
             */
            FileKeeper.getInstance().finishDeleteFiles();
        } catch (CinnamonException e) {
            initializeLocalMessage();
            XmlResponse resp = new XmlResponse(res);
            Element root = resp.getDoc().addElement("error");
            if (em != null && em.isOpen() && LocalMessage.wasInitialized()) {
                e.addToElement(root, LocalMessage.getInstance().get());
            } else {
                e.addToElement(root, null);
            }
            log.debug("CinnamonException:\n" + resp.getDoc().asXML(), e);
            response = resp;
            rollback(etx);
        } catch (Exception exception) {
            log.debug("An exception occurred: ", exception);

            Throwable cause = exception.getCause();
            Throwable oldCause = null;
            while (cause != oldCause) {
                Throwable newCause = cause.getCause();
                oldCause = cause;
                if (newCause != null) {
                    cause = newCause;
                }
            }

            StringBuilder message = new StringBuilder();
            if (cause == null) {
                message.append(exception.getLocalizedMessage());
                message.append("\n");
                for (StackTraceElement ste : exception.getStackTrace()) {
                    message.append(ste);
                    message.append("\n");
                }
                cause = exception; // so we have something to write home about.
            } else {
                message.append(cause.toString());
                log.debug("message: " + cause.toString());
            }

            rollback(etx);
            XmlResponse resp = new XmlResponse(res);
            Element root = resp.getDoc().addElement("error");
            root.addElement("code").addText(cause.toString());
            root.addElement("message").addText(message.toString());
            response = resp;

        } finally {
            LocalRepository.cleanUp();
            if (conf.getUseSessionLogging()) {
                clearSessionLogging();
            }
            if (getEm() == null) {
                log.debug("EM is null");
            } 
            else {
                if (getEm().isOpen()) {
                    log.debug("closing em");
                    getEm().close();
                }
            }

            /*
                * Debugging: we encountered a bug where the response object was null,
                * without an obvious cause.
                */
            if (response == null) {
                log.warn("Response is null! Please check previous log messages!");
                log.warn("Creating error response.");
                response = new XmlResponse(res,
                        "<error>Null-Response-Error encountered. " +
                                "Please check server logs and report your findings to the administrator." +
                                "</error>");
            }

            response.write();
        }
    }

    void initializeLocalMessage() {
        if (em == null || !em.isOpen()) {
            log.warn("Cannot initialize localMessage without database connection.");
            return;
        }
        MessageDAO mDao = daoFactory.getMessageDAO(em);
        UiLanguageDAO lDao = daoFactory.getUiLanguageDAO(em);
        UiLanguage undetermined = lDao.findByIsoCode("und");
        if(undetermined == null){
            log.error("Could not find the UiLanguage for 'und' in the database. Please fix your setup.");
            return;
        }
        LocalMessage.initializeLocalMessage(mDao, undetermined);
    }

    void rollback(EntityTransaction etx) {
        try {
            if (etx != null && etx.isActive()) {
                log.debug("Exception occurred => rollback database.");
                etx.rollback();
            }
            if(repository.getAuditConnection() != null){
                log.debug("rollback audit log");
                repository.getAuditConnection().rollback();
            }
        } catch (Exception re) {
            log.error("Failed to rollback; " + re.getMessage());
        }
    }

    public Throwable getRootCause(Throwable e) {
        if (e.getCause() == null) {
            return e.getCause();
        } else {
            return getRootCause(e.getCause());
        }
    }

    private void clearSessionLogging() {
        MDC.remove("user");
        MDC.remove("session");
        MDC.remove("repo");
        MDC.remove("repoUser");
        MDC.remove("repoUserSession");
    }

    private void activateSessionLogging(String ticket, Repository repository, Long sessionId, String username) {
        log.debug("activate session logging for session " + sessionId);
        MDC.put("user", username);
        MDC.put("session", ticket);
        MDC.put("repo", repository.getName());
        MDC.put("repoUser", repository.getName() + "_" + username);
        String repoUserSession = String.format("%s__%s__%s", repository.getName(), username, sessionId);
        MDC.put("repoUserSession", repoUserSession);
    }

    /**
     * The connect command creates a session with the specified parameters.
     * The session is stored in the sessions table of the underlying RDBMS.
     * The command returns a session ticket that uniquely identifies the session.
     * <p/>
     * The request contains the following parameters:
     * <ul>
     * <li>command="connect"</li>
     * <li>repository=repository name</li>
     * <li>user=user name</li>
     * <li>pwd=password</li>
     * <li>machine=machine name</li>
     * <li>[language]= optional: ISO code of the client's language.</li>
     * </ul>
     *
     * @param cmd HTTP request params as Map
     * @return a Response containing
     *         <pre>
     *         {@code
     *         <connection>
     *            <ticket>$ticket</ticket>
     *         </connection>
     *         }
     *         </pre>
     *         or an XML error message.
     */
    @CinnamonMethod(checkTrigger = "true")
    public Response connect(Map<String, String> cmd) {
        XmlResponse resp = new XmlResponse(res);
        String repository = cmd.get("repository");
        String username = cmd.get("user");
        String password = cmd.get("pwd");
        String machine = cmd.get("machine");
        String lang = cmd.get("language");

        log.debug("Looking up User in DB");
        User user;
        try {
            UserDAO userDAO = daoFactory.getUserDAO(em);
            user = userDAO.findByName(username);
        } catch (NoResultException e) {
            throw new CinnamonException("error.user_not_found");
        }

        Boolean loginOk;
        if (conf.getField("encryptPasswords", "false").equals("true")) {
            // passwords are stored in encrypted form
            log.debug("passwords are stored encrypted");
            loginOk = HashMaker.compareWithHash(password, user.getPwd());
        } else {
            /*
            * Some databases have a case-insensitive default collation,
            *  so we have to check the password explicitly.
            */
            loginOk = user.getPwd().equals(password);
        }
        if (!loginOk) {
            throw new CinnamonException("error.wrong.password");
        }

        log.debug("Found User in DB with correct password");
        UiLanguageDAO langDao = daoFactory.getUiLanguageDAO(em);
        UiLanguage language;
        try {
            // when in doubt, use default language.
            if(lang == null){
                lang = "und";
            }
            language = langDao.findByIsoCode(lang);
        } catch (Exception e) {
            log.debug("Client did not supply a valid language name, will use 'und' for undetermined language.");
            language = langDao.findByIsoCode("und"); // choose 'undetermined' as language.
        }
        
        if(user.getLanguage() == null){
            // if user does not already have a valid language, set it now.
            user.setLanguage(language);
        }
       
        Session session = new Session(repository, user, machine, language);
        session.renewSession(ConfThreadLocal.getConf().getSessionExpirationTime(repository));
        SessionDAO sessionDAO = daoFactory.getSessionDAO(em);
        sessionDAO.makePersistent(session);
        log.debug("session.ticket:" + session.getTicket());
        Element connection = resp.getDoc().addElement("connection");
        connection.addElement("ticket").addText(session.getTicket());

        return resp;
    }

    /**
     * Fork a session and receive another session ticket for the current repository.
     * This method should be used by multi-threaded clients, which must not share
     * the same ticket over parallel requests.
     *
     * @param cmd HTTP request params as Map
     *            The request contains the following parameters:
     *            <ul>
     *            <li>command=forksession</li>
     *            <li>ticket=current session ticket</li>
     * @return a Response containing
     *         <pre>
     *         {@code
     *         <connection>
     *            <ticket>$ticket</ticket>
     *         </connection>
     *         }
     *         </pre>
     *         or an XML error message.
     */
    @CinnamonMethod(checkTrigger = "true")
    public Response forkSession(Map<String, String> cmd) {
        XmlResponse resp = new XmlResponse(res);
        String ticket = cmd.get("ticket");
        SessionDAO sessionDAO = daoFactory.getSessionDAO(em);
        Session session;
        try {
            session = sessionDAO.findByTicket(ticket);
        } catch (NoResultException e) {
            throw new CinnamonException("error.unknown.ticket");
        }
        // clone session
        String repository = ticket.split("@")[1];
        Session forkedSession = session.copy(repository);
        sessionDAO.makePersistent(forkedSession);
        log.debug("forked.session.ticket:" + forkedSession.getTicket());
        Element connection = resp.getDoc().addElement("connection");
        connection.addElement("ticket").addText(forkedSession.getTicket());
        return resp;
    }

    /**
     * The sendPasswordMail command sends a password reset mail to the user specified in the request.
     * <p/>
     * The request contains the following parameters:
     * <ul>
     * <li>command=sendpasswordmail</li>
     * <li>repository=repository name</li>
     * <li>login=login name</li>
     * </ul>
     *
     * @param cmd HTTP request params as Map
     * @return a Response containing
     *         <pre>
     *                         {@code
     *                         	<success>success.sent.mail</success>
     *                         }
     *                         </pre>
     */
    @CinnamonMethod(checkTrigger = "true")
    public Response sendPasswordMail(Map<String, String> cmd) {
        /*
           * 1. get user from "login"
           * 2. get email from user.email (check ! null)
           * 3. create UUID token (check ! null)
           * 4. generate link
           * 5. send mail
           */

        // lookup user
        UserDAO uDao = daoFactory.getUserDAO(em);
        User user = uDao.findByName(cmd.get("login"));
        if (user == null) {
            throw new CinnamonException("error", "error.param.loginname");
        }

        // lookup email
        String email = user.getEmail();
        if (email == null) {
            throw new CinnamonException("error.invalid.user.email");
        }

        try {
            User myUser = uDao.get(user.getId()); // get managed user object
            myUser.createToken();

            TemplateProvider templateProvider = new TemplateProvider();
            Template template =
                    templateProvider.fetchTemplate(user.getLanguage(), "reset_password_mail.vt");
            VelocityContext context = templateProvider.addTemplateParams(template, user, repository.getName());
            StringWriter mailWriter = new StringWriter();
            template.merge(context, mailWriter);

            MailSender mailSender = new MailSender();
            String mailFrom = ConfThreadLocal.getConf().getField("mail/from", "CinnamonServer@localhost");

            log.debug("initializeLocalMessage");
            MessageDAO mDao = daoFactory.getMessageDAO(em);
            LocalMessage.initializeLocalMessage(mDao, user.getLanguage());

            log.debug("sending password mail to: " + user.getEmail());
            Boolean mailSent = mailSender.sendMail(mailFrom, user.getEmail(),
                    LocalMessage.loc("password.reset.mail.subject"), mailWriter.toString());
            if (!mailSent) {
                log.warn("Sending password reset mail to user " + user.getName() + " failed.");
                Exception fail = mailSender.getFail();
                log.warn("Mail error:", fail);
                throw new RuntimeException(fail);
            }
        } catch (Exception e) {
            log.error("error in email validation:\n", e);
            throw new CinnamonException("error.sending.mail", e);
        }

        XmlResponse response = new XmlResponse(res);
        response.addTextNode("success", "success.sent.mail");
        return response;
    }

    /**
     * The user may change his email address by calling this method.
     * The setEmail command sends an email validation mail to the user specified in the request.
     *
     * @param cmd HTTP request parameter map:
     *            <ul>
     *            <li>command=setemail</li>
     *            <li>email=new email address</li>
     *            </ul>
     * @return a Response containing
     *         <pre>
     *                         {@code
     *                         	<success>success.sent.mail</success>
     *                         }
     *                         </pre>
     */
    @CinnamonMethod(checkTrigger = "true")
    public Response setEmail(Map<String, String> cmd) {
        /*
           * 1. get email from user.email (check ! null)
           * 2. create UUID token (check ! null)
           * 3. generate link
           * 4. send mail
           */

        // lookup email
        String email = cmd.get("email");
        if (email == null) {
            throw new CinnamonException("error.param.email");
        }

        try {
            user.createToken();

            TemplateProvider templateProvider = new TemplateProvider();
            Template template =
                    templateProvider.fetchTemplate(user.getLanguage(), "validate_email.vt");
            VelocityContext context = templateProvider.addTemplateParams(template, user, repository.getName());
            context.put("email", email.replaceAll("@", "%40")); // encode @ for validation-URL
            StringWriter mailWriter = new StringWriter();

            template.merge(context, mailWriter);

            MailSender mailSender = new MailSender();
            String mailFrom = ConfThreadLocal.getConf().getField("mail/from", "CinnamonServer@localhost");

            log.debug("sending validation mail to: " + email);
            Boolean mailSent = mailSender.sendMail(mailFrom, email,
                    LocalMessage.loc("validate.email.subject"), mailWriter.toString());
            if (!mailSent) {
                log.warn("Sending password reset mail to user " + user.getName() + " failed.");
                Exception fail = mailSender.getFail();
                log.warn("Mail error:", fail);
                throw new RuntimeException(fail);
            }
        } catch (Exception e) {
            log.error("error in email validation:\n", e);
            throw new CinnamonException("error.set.mail", e);
        }

        XmlResponse response = new XmlResponse(res);
        response.addTextNode("success", "success.sent.mail");
        return response;
    }

    /**
     * <p>
     * The create command creates an object in the repository with the given name.
     * The object name need not be unique for the same parent. Objects can not be created in root,
     * they must be created in a folder. If a file is specified, the format must also be specified.
     * The value in the name column of the formats table must be used. The id of the newly created
     * object is returned.</p>
     * If object creation fails, an error message is returned.
     * If no file parameter is specified, an object without content is created. The setcontent
     * command can be used to add content later.<br>
     * <h2>Needed permissions</h2>
     * CREATE_OBJECT
     *
     * @param cmd HTTP request parameter map
     *            <ul>
     *            <li>command=create</li>
     *            <li>ticket=session ticket</li>
     *            <li>[preid]= optional predecessor id - basically this may be used to create another (empty) version of an object.</li>
     *            <li>name = name of this object</li>
     *            <li>[appname] = internally used by desktop client to determine which DTDs etc are needed for this object</li>
     *            <li>metadata = XML string of metadata with required root element {@code <meta>}</li>
     *            <li>[objtype_id OR objtype]= Id or Name of object type. If no object type is specified, use Constants.OBJTYPE_DEFAULT.</li>
     *            <li>parentid = id of parent folder</li>
     *            <li>[format OR format_id] = Id or name of format (optional)</li>
     *            <li>[acl_id] = optional Id of ACL - if not specified, will use ACL of parent folder</li>
     *            <li>[language_id]=optional id of language, will use default language "und" for undetermined if not specified.</li>
     *            </ul>
     * @return a Response which contains:
     *         <pre>
     *                         {@code <objectId>$id_of_new_object</objectId>}
     *                         </pre>
     * @throws IOException if upload of file content fails
     */
    @CinnamonMethod(checkTrigger = "true")
    public Response create(Map<String, Object> cmd) throws IOException {
        User user = getUser();
        ObjectSystemData osd = new ObjectSystemData(cmd, user, false);
        (new Validator(user)).validateCreate(osd.getParent());
        log.debug("osd created: " + osd);

        if (cmd.containsKey("file")) {
            String contentPath = ContentStore.upload((UploadedFile) cmd.get("file"), repository.getName());
            osd.setContentPath(contentPath, repository.getName());
            if (osd.getContentPath() != null &&
                    osd.getContentPath().length() == 0) {
                throw new CinnamonException("error.storing.upload");
            }
            new TikaParser().parse(osd, repository.getName());
        }

        log.debug("about to create object");
        ObjectSystemDataDAO osdDao = daoFactory.getObjectSystemDataDAO(em);
        osdDao.makePersistent(osd);

        new MetasetService().initializeMetasets(osd, (String) cmd.get("metasets"));
        
        XmlResponse resp = new XmlResponse(res);
        resp.addTextNode("objectId", String.valueOf(osd.getId()));
        return resp;
    }


    /**
     * Generate a list of one or all groups.
     *
     * @param cmd HTTP request parameter map:
     *            <ul>
     *            <li>command=listgroups</li>
     *            <li>[optional: groupid=id to retrieve a single group]</li>
     *            <li>ticket = session ticket</li>
     *            </ul>
     * @return XML-Response in the format
     *         <pre>
     *          {@code
     *            <groups>
     *              <group><id>123</id>...</group>
     *              ...
     *              </groups>
     *         }
     *         </pre>
     */
    @CinnamonMethod(checkTrigger = "true")
    public Response listGroups(Map<String, String> cmd) {
        XmlResponse resp = new XmlResponse(res);
        Document result = resp.getDoc();
        Element root = result.addElement("groups");

        Long id;
        GroupDAO groupDAO = daoFactory.getGroupDAO(em);
        List<Group> groups = new ArrayList<Group>();
        if (cmd.containsKey("groupid")) {
            id = ParamParser.parseLong(cmd.get("groupid"), "error.param.group_id");
            groups.add(groupDAO.get(id));
        } else {
            groups = groupDAO.list();
        }

        for (Group g : groups) {
            g.toXmlElement(root);
        }
        log.trace("Created group-List as XML:" + result.asXML());
        return resp;
    }

    /**
     * The copy command creates a new object in the folder specified by targetfolderid as a copy
     * of the object specified by the sourceobjid parameter.<br>
     * <br>
     * <h2>Needed permissions</h2>
     * <ul>
     * <li>READ_OBJECT_CONTENT</li>
     * <li>READ_OBJECT_CUSTOM_METADATA</li>
     * <li>READ_OBJECT_SYS_METADATA</li>
     * <li>CREATE_OBJECT</li>
     * </ul>
     *
     * @param cmd parameter map from HTTP request containing:
     *            <ul>
     *            <li>command = copy</li>
     *            <li>sourceid	= source object id</li>
     *            <li>targetfolderid	= target folder id</li>
     *            <li>[metasets]=optional, comma-separated list of metasetType-names which will be copied.</li>
     *            <li>ticket = session ticket</li>
     *            </ul>
     * @return xml response with id of newly created object (or standard xml error message in case of error):
     * <pre>
     *     {@code
     *     <objectId>12345</objectId>
     *     }
     * </pre>
     */
    @CinnamonMethod(checkTrigger = "true")
    public Response copy(Map<String, String> cmd) {
        Long sourceId = ParamParser.parseLong(cmd.get("sourceid"), "error.param.source_id");
        Long targetFolderId =
                ParamParser.parseLong(cmd.get("targetfolderid"), "error.param.targetfolder_id");

        // fetch target folder
        FolderDAO folderDAO = daoFactory.getFolderDAO(em);
        Folder targetFolder = folderDAO.get(targetFolderId);
        if (targetFolder == null) {
            throw new CinnamonException("error.folder.not_found");
        }

        // fetch source object
        ObjectSystemDataDAO osdDAO = daoFactory.getObjectSystemDataDAO(em);
        ObjectSystemData osd = osdDAO.get(sourceId);
        if (osd == null) {
            throw new CinnamonException("error.object.not.found");
        }
        (new Validator(user)).validateCopy(osd, targetFolder);

        ObjectSystemData copy = osd.createClone();
        copy.setAcl(targetFolder.getAcl()); // set ACL to target folder's ACL
        copy.setParent(targetFolder);
        copy.setName("Copy_" + osd.getName());
        copy.setPredecessor(null);
        copy.setOwner(getUser());
        copy.setVersion("1");
        copy.setLatestBranch(true);
        copy.setLatestHead(true);
        copy.setRoot(copy);
        copy.setModifier(user);
        copy.setCreator(user);
        copy.setLocked_by(null);

        // copy content
        String repositoryName = repository.getName();
        osd.copyContent(repositoryName, copy);

//        log.debug("current object id before final commit: " + copy.getId());
        osdDAO.makePersistent(copy);
        log.debug("current object id after make persistence: " + copy.getId());

        log.debug("copy relations");
        // copy relations:
        osd.copyRelations(copy);

        // execute the new LifeCycleState if necessary.
        if (copy.getState() != null) {
            copy.getState().enterState(copy, copy.getState(), repository, user);
        }

        XmlResponse resp = new XmlResponse(res);
        resp.addTextNode("objectId", String.valueOf(copy.getId()));
        return resp;
    }


    /**
     * The copyfolder command creates a new folder inside the target folder and copies
     * the content of the source folder.
     * <p/>
     * <h2>Needed permissions</h2>
     * <ul>
     * <li>READ_OBJECT_CONTENT</li>
     * <li>READ_OBJECT_CUSTOM_METADATA</li>
     * <li>READ_OBJECT_SYS_METADATA</li>
     * <li>CREATE_OBJECT</li>
     * </ul>
     *
     * @param cmd parameter map from HTTP request containing:
     *            <ul>
     *            <li>command          = copyfolder</li>
     *            <li>source_folder	= source folder id</li>
     *            <li>target_folder	= target folder id</li>
     *            <li>ticket           = session ticket</li>
     *            <li>[versions]       = all,branch,head (optional,
     *            defaults to 'head', meaning only the newest version is used.)</li>
     *            <li>[croak_on_error]   = 'true' or 'false' - if true, stop in case an error occurs.
     *            Otherwise, copy as much as possible and report problems. Default is 'false'.</li>
     *            </ul>
     * @return XML response with ids of newly created folders and objects (xml error message in case of failure)
     */
    @CinnamonMethod(checkTrigger = "true")
    public Response copyFolder(Map<String, String> cmd) {
        Long sourceFolderId = ParamParser.parseLong(cmd.get("source_folder"), "error.param.source_folder");
        Long targetFolderId =
                ParamParser.parseLong(cmd.get("target_folder"), "error.param.target_folder");

        // fetch target folder
        FolderDAO folderDao = daoFactory.getFolderDAO(em);
        Folder targetFolder = folderDao.get(targetFolderId);
        if (targetFolder == null) {
            throw new CinnamonException("error.folder.not_found");
        }
        Folder sourceFolder = folderDao.get(sourceFolderId);
        if (sourceFolder == null) {
            throw new CinnamonException("error.folder.not_found");
        }

        Boolean croakOnError = false;
        if (cmd.containsKey("croak_on_error")) {
            croakOnError = ParamParser.parseBoolean(cmd.get("croak_on_error"), "error.param.croak_on_error");
        }
        String versions = cmd.get("versions");
        if (versions == null) {
            versions = "head";
        }

        CopyResult copyResult = sourceFolder.copyFolder(targetFolder, croakOnError, versions, user);

        return new XmlResponse(res, copyResult.toXml());
    }

    /**
     * The copyAllVersions command creates a new object tree in the folder specified by target_folder_id
     * as a copy of the source object's tree.
     * <p/>
     * <h2>Needed permissions</h2>
     * <ul>
     * <li>READ_OBJECT_CONTENT</li>
     * <li>READ_OBJECT_CUSTOM_METADATA</li>
     * <li>READ_OBJECT_SYS_METADATA</li>
     * <li>CREATE_OBJECT</li>
     * </ul>
     *
     * @param cmd HTTP request parameter map:
     *            <ul>
     *            <li>command=copyallversions</li>
     *            <li>source_id	= source object id</li>
     *            <li>target_folder_id	= target folder id</li>
     *            <li>ticket=session ticket</li>
     *            </ul>
     * @return The object data of the newly created object tree as an XML document (via response object)
     */
    @CinnamonMethod(checkTrigger = "true")
    public Response copyAllVersions(Map<String, String> cmd) {
        Long sourceId = ParamParser.parseLong(cmd.get("source_id"), "error.param.source_id");
        Long targetFolderId =
                ParamParser.parseLong(cmd.get("target_folder_id"), "error.param.target_folder_id");
        // TODO: use either target_folder_id or target_folder in copy and copyAllVersions.

        // fetch target folder
        FolderDAO folderDAO = daoFactory.getFolderDAO(em);
        Folder targetFolder = folderDAO.get(targetFolderId);
        if (targetFolder == null) {
            throw new CinnamonException("error.folder.not_found");
        }

        // fetch source object
        ObjectSystemDataDAO osdDao = daoFactory.getObjectSystemDataDAO(em);
        ObjectSystemData sourceOsd = osdDao.get(sourceId);
        if (sourceOsd == null) {
            throw new CinnamonException("error.object.not.found");
        }

        /*
         * Validate access permission.
         * If we encounter a missing permission, we do not need to do any more work here.
         */
        Validator val = new Validator(user);
        for (ObjectSystemData osd : osdDao.findAllVersions(sourceOsd)) {
            // possible performance optimization: check only once for targetFolder)
            log.debug("copyAllVersions::validate::{}", osd.getId());
            val.validateCopy(osd, targetFolder);
        }


        // create a copy of the object tree
        List<ObjectSystemData> cloneArmy = osdDao.copyObjectTree(sourceOsd, targetFolder, getUser(), repository);

        RelationDAO relationDAO = daoFactory.getRelationDAO(em);
        /*
         * copy relations and re-index object.
         */
        for (ObjectSystemData clone : cloneArmy) {

            List<Relation> relations = relationDAO.findAllByLeftID(clone.getId());
            for (Relation rel : relations) {
                Relation relCopy = new Relation(rel.getType(), clone, rel.getRight(), rel.getMetadata());
                log.debug("current object id before persists_relation: {} ", clone.getId());
                relationDAO.makePersistent(relCopy);
                log.debug("current object id after persists_relation: {} ", clone.getId());
                log.debug("relation-id: {}", relCopy.getId());
            }
        }


        XmlResponse resp = new XmlResponse(res);
        /*
         List of copies is generated last-to-first, while getObjects is first-to-last.
         The order is reversed to enable easier comparison of generated XML in tests.
         */
        Collections.reverse(cloneArmy);

        resp.setDoc(ObjectSystemData.generateQueryObjectResultDocument(cloneArmy));
        return resp;
    }


    /**
     * The createfolder command creates a folder in the repository with the given name.
     * The folder name must be unique for the same parent. To create a subfolder of root,
     * specify 0 as parent id. The id of the newly created folder is returned.<br>
     * <br>
     * The metadata must be specified in one row.
     *
     * @param cmd HTTP request parameter map:
     *            <ul>
     *            <li>command=createfolder</li>
     *            <li>metadata=xml metadata (optional)</li>
     *            <li>name=folder name</li>
     *            <li>parentid=parent folder id</li>
     *            <li>aclid=id of the new folder's acl</li>
     *            <li>ownerid=id of the folder's owner</li>
     *            <li>[typeid]= id of the folderType (if not set, use default_folder_type)</li>
     *            <li>ticket=session ticket</li>
     *            </ul>
     * @return XML-Response:
     *         Folder serialized to XML.
     *         <h2>Needed permissions</h2>
     *         CREATE_FOLDER
     */
    @CinnamonMethod(checkTrigger = "true")
    public Response createFolder(Map<String, String> cmd) {
        FolderDAO folderDao = daoFactory.getFolderDAO(em);
        Long parentId = ParamParser.parseLong(cmd.get("parentid"), "error.param.parent_id");
        Folder parentFolder;
        if (parentId == 0L) { // 0 is considered the root folder.
            parentFolder = folderDao.findRootFolder();
        } else {
            parentFolder = folderDao.get(cmd.get("parentid"));
        }

        (new Validator(user)).validateCreateFolder(parentFolder);
        log.info("about to create new folder");
        Folder folder = new Folder(cmd, getUser());
        log.info("created new folder-object");

        folderDao.makePersistent(folder);
        log.debug("repository: " + repository.getName());
        
        log.debug("CreateFolderId: " + folder.getId());
        XmlResponse resp = new XmlResponse(res);
        Element root = resp.getDoc().addElement("folders");
        folder.toXmlElement(root);
        return resp;
    }

    /**
     * Get an XML List of one or all ACLs.
     *
     * @param cmd Map with Key/Value-Pair<br>
     *            <ul>
     *            <li>[id] = Id of an ACL (integer)</li>
     *            </ul>
     * @return XML-Response
     *         <pre>
     *                          {@code
     *                          <acls>
     *                            <acl>
     *                              <id>1</id>
     *                              <name>localized name</name>
     *                              <sysName>raw message id, like "_default_acl"</sysName>
     *                              <description> description of this acl </description>
     *                            </acl>
     *                            ...
     *                          </acl>
     *                         }
     *                         </pre>
     */
    @CinnamonMethod(checkTrigger = "true")
    public Response getAcls(Map<String, String> cmd) {
        XmlResponse resp = new XmlResponse(res);
        Document doc = resp.getDoc();
        AclDAO aclDAO = daoFactory.getAclDAO(em);
        List<Acl> results = new ArrayList<Acl>();
        if (cmd.containsKey("id")) {
            Long aclId = ParamParser.parseLong(cmd.get("id"), "error.param.id");
            results.add(aclDAO.get(aclId));
        } else {
            results = aclDAO.list();
        }
        Element root = doc.addElement("acls");
        for (Acl acl : results) {
            acl.toXmlElement(root);
        }
        return resp;
    }

    /**
     * Get a specific AclEntry.
     *
     * @param cmd Map with Key/Value-Pair<br>
     *            <ul>
     *            <li>id = Id of an AclEntry (integer)</li>
     *            </ul>
     * @return XML-Response with serialized AclEntry
     *         <pre>
     *                         {@code
     *                          <aclEntries>
     *                              <aclEntry>...</aclEntry>
     *                          </aclEntries>
     *                         }
     *                         </pre>
     * @see server.AclEntry
     */
    @CinnamonMethod(checkTrigger = "true")
    public Response getAclEntry(Map<String, String> cmd) {
        XmlResponse resp = new XmlResponse(res);
        Long id = ParamParser.parseLong(cmd.get("id"), "error.param.id");

        AclEntryDAO aeDAO = daoFactory.getAclEntryDAO(em);
        AclEntry ae = aeDAO.get(id);
        if (ae == null) {
            throw new CinnamonException("error.object.not.found");
        }
        Element root = resp.getDoc().addElement("aclEntries");
        ae.toXmlElementWithPermissions(root);
        return resp;
    }

    /**
     * Retrieve a list of all ACLs an user is a member of.
     *
     * @param cmd HTTP request parameter map
     *            <ul>
     *            <li>[id] = Id of an User (long)</li>
     *            </ul>
     * @return XML-Response
     * @see CmdInterpreter#getAcls(java.util.Map)
     */
    @CinnamonMethod(checkTrigger = "true")
    public Response getUsersAcls(Map<String, String> cmd) {
        User user = getUser();
        log.debug("groupUsers for user " + user.getName() + ": " + user.getGroupUsers().size());

        Set<Group> groups = new HashSet<Group>();
        for (GroupUser gu : user.getGroupUsers()) {
            groups.add(gu.getGroup());
            groups.addAll(gu.getGroup().findAncestors());
        }
        log.debug("number of groups for this user: " + groups.size());

        Set<Acl> acls = new HashSet<Acl>();
        for (Group group : groups) {
            /*
                    * If there are many groups whose AclEntries point to the
                    * same Acls, it could be better to first collect the
                    * entries before adding their Acls.
                    */
            for (AclEntry ae : group.getAclEntries()) {
                acls.add(ae.getAcl());
            }
        }
        log.debug("number of acls for this user: " + acls.size());

        XmlResponse resp = new XmlResponse(res);
        Element root = resp.getDoc().addElement("acls");

        for (Acl acl : acls) {
            acl.toXmlElement(root);
        }
        return resp;
    }

    /**
     * Retrieve a list of all Permissions applicable for a user on
     * a given Acl. For superusers, it returns all permissions, as those
     * are not restricted by ACLs / permissions.
     *
     * @param cmd Map with Key/Value-Pair<br>
     *            <ul>
     *            <li>userId = Id of a User</li>
     *            <li>aclId = Id of an Acl</li>
     *            </ul>
     * @return XML-Response, for format see listPermissions command.
     * @see CmdInterpreter#listPermissions(java.util.Map)
     */
    @CinnamonMethod(checkTrigger = "true")
    public Response getUsersPermissions(Map<String, String> cmd) {
        UserDAO userDao = daoFactory.getUserDAO(em);
        Long userId = ParamParser.parseLong(cmd.get("userId"), "error.param.id");
        User user = userDao.get(userId);
        if (user == null) {
            throw new CinnamonException("error.user.not_found");
        }
        if (user.verifySuperuserStatus(em)) {
            return listPermissions(cmd);
        }

        log.debug("groupUsers for user " + user.getName() + ": " + user.getGroupUsers().size());

        Set<Group> groups = user.findAllGroups();
        log.debug("number of groups for this user: " + groups.size());

        Long aclParam = ParamParser.parseLong(cmd.get("aclId"), "error.param.acl_id");
        Set<Permission> permissions = new HashSet<Permission>();
        for (Group group : groups) {
            /*
                    * If there are many groups whose AclEntries point to the
                    * same Acls, it could be better to first collect the
                    * entries before adding their Permissions.
                    */
            log.debug("working on group:" + group.getName());
            for (AclEntry ae : group.getAclEntries()) {
                log.debug("working on AclEntry for Acl:" + ae.getAcl().getName());
                Long aclId = ae.getAcl().getId();
                if (aclId.equals(aclParam)) {
                    log.debug("found acl");
                    Set<AclEntryPermission> aepSet = ae.getAePermissions();
                    for (AclEntryPermission aep : aepSet) {
                        permissions.add(aep.getPermission());
                    }
                }
            }
        }
        log.debug("number of permissions for this user: " + permissions.size());
        XmlResponse resp = new XmlResponse(res);
        Element root = resp.getDoc().addElement("permissions");
        for (Permission permission : permissions) {
            permission.toXmlElement(root);
        }
        return resp;
    }

    /**
     * Retrieve a list of all Groups an user is a member of.
     *
     * @param cmd Map with Key/Value-Pair<br>
     *            <ul>
     *            <li>id = Id of an User (long)</li>
     *            <li>[recursive] = "true" or "false", whether Cinnamon should recursively include parent groups<li>
     *            </ul>
     * @return XML-Response listing the groups of this user.
     * @see CmdInterpreter#listGroups(java.util.Map)
     */
    @CinnamonMethod(checkTrigger = "true")
    public Response getGroupsOfUser(Map<String, String> cmd) {
        Long id = ParamParser.parseLong(cmd.get("id"), "error.param.id");
        UserDAO userDao = daoFactory.getUserDAO(em);
        User user = userDao.get(id);
        log.debug("groups for user " + user.getName() + ": " + user.getGroupUsers().size());

        Set<Group> groups = new HashSet<Group>();

        Boolean recursive = cmd.containsKey("recursive") && cmd.get("recursive").equals("true");
        for (GroupUser gu : user.getGroupUsers()) {
            groups.add(gu.getGroup());
            if (recursive) {
                groups.addAll(gu.getGroup().findAncestors());
            }
        }
        log.debug("number of groups for this user: " + groups.size());

        XmlResponse resp = new XmlResponse(res);
        Element root = resp.getDoc().addElement("groups");
        for (Group g : groups) {
            g.toXmlElement(root);
        }
        return resp;
    }


    /**
     * Retrieve a list of all users which are connected to an ACL via their groups.
     * Note: to determine if a user has a set of permissions, you need to call listAclEntries
     * with his groups / this ACL id.
     *
     * @param cmd Map with Key/Value-Pair<br>
     *            <ul>
     *            <li>id = id of the ACL</li>
     *            </ul>
     * @return XML-Response
     * @see CmdInterpreter#getUsers(java.util.Map)
     */
    @CinnamonMethod(checkTrigger = "true")
    public Response listAclMembers(Map<String, String> cmd) {
        User user = getUser();
        log.debug("groupUsers for user " + user.getName() + ": " + user.getGroupUsers().size());

        AclDAO aclDao = daoFactory.getAclDAO(em);
        Long aclId = ParamParser.parseLong(cmd.get("id"), "error.param.id");
        Acl acl = aclDao.get(aclId);

        Set<Group> groups = new HashSet<Group>();
        for (AclEntry ae : acl.getAclEntries()) {
            Group g = ae.getGroup();
            groups.add(g);
            groups.addAll(g.findChildren());
        }

        Set<User> users = new HashSet<User>();
        for (Group group : groups) {
            for (GroupUser gu : group.getGroupUsers()) {
                users.add(gu.getUser());
            }
        }

        XmlResponse resp = new XmlResponse(res);
        Element root = resp.getDoc().addElement("users");
        for (User u : users) {
            root.add(User.asElement("user", u));
        }

        return resp;
    }

    /**
     * Retrieve a list of all AclEntries for a group or acl.
     *
     * @param cmd Map with Key/Value-Pair<br>
     *            <ul>
     *            <li>[aclid] = id of an Acl</li>
     *            <li>[groupid] = id of a Group</li>
     *            </ul>
     * @return XML-Response with serialized AclEntries.
     * @see CmdInterpreter#getAclEntry(java.util.Map)
     * @see server.AclEntry
     */
    @CinnamonMethod(checkTrigger = "true")
    public Response listAclEntries(Map<String, String> cmd) {
        Set<AclEntry> aclEntries;
        if (cmd.containsKey("aclid")) {
            AclDAO aclDao = daoFactory.getAclDAO(em);
            Long id = ParamParser.parseLong(cmd.get("aclid"), "error.invalid.acl_id");
            Acl acl = aclDao.get(id);
            if (acl == null) {
                throw new CinnamonException("error.object.not.found");
            }
            aclEntries = acl.getAclEntries();
        } else if (cmd.containsKey("groupid")) {
            GroupDAO groupDao = daoFactory.getGroupDAO(em);
            Long id = ParamParser.parseLong(cmd.get("groupid"), "error.invalid.acl_id");
            Group group = groupDao.get(id);
            if (group == null) {
                throw new CinnamonException("error.object.not.found");
            }
            aclEntries = group.getAclEntries();
        } else {
            throw new CinnamonException("error.missing_acl_or_group_id");
        }

        XmlResponse resp = new XmlResponse(res);
        Element root = resp.getDoc().addElement("aclEntries");
        for (AclEntry ae : aclEntries) {
            ae.toXmlElementWithPermissions(root);
        }
        return resp;
    }


    /**
     * @param cmd HTTP request parameter map:
     *            <ul>
     *            <li>command=getpermission</li>
     *            <li>id=id of the permission object</li>
     *            </ul>
     * @return XML-Response:
     *         XML-String representing the requested Permission object - or an XML-wrapped error message.
     * @see server.Permission
     */
    @CinnamonMethod(checkTrigger = "true")
    public Response getPermission(Map<String, String> cmd) {
        Long id = ParamParser.parseLong(cmd.get("id"), "error.param.id");
        PermissionDAO pDao = daoFactory.getPermissionDAO(em);
        Permission permission = pDao.get(id);
        if (permission == null) {
            throw new CinnamonException("error.object.not.found");
        }

        XmlResponse resp = new XmlResponse(res);
        Element root = resp.getDoc().addElement("permissions");
        permission.toXmlElement(root);
        return resp;

    }


    /**
     * @param cmd HTTP request parameter map:
     *            <ul>
     *            <li>command=listpermissions</li>
     *            <li>ticket=session ticket</li>
     *            </ul>
     * @return XML-Response with a list of all Permission objects.
     *         <pre>
     *                          {@code
     *                          <permissions>
     *                              <permission><id>777</id>....</permission>
     *                              ...
     *                          </permissions>
     *                         }
     *                         </pre>
     * @see server.Permission
     */
    @CinnamonMethod(checkTrigger = "true")
    public Response listPermissions(Map<String, String> cmd) {
        PermissionDAO pDao = daoFactory.getPermissionDAO(em);
        List<Permission> permissions = pDao.list();

        XmlResponse resp = new XmlResponse(res);
        Element root = resp.getDoc().addElement("permissions");
        for (Permission p : permissions) {
            p.toXmlElement(root);
        }
        return resp;
    }

    /**
     * @param cmd HTTP request parameter map:
     *            <ul>
     *            <li>command=listlanguages</li>
     *            </ul>
     * @return XML-Response with a list of all Language objects - or an XML-wrapped error message.
     * @see server.i18n.Language
     */
    @CinnamonMethod(checkTrigger = "true")
    public Response listLanguages(Map<String, String> cmd) {
        LanguageDAO lDao = daoFactory.getLanguageDAO(em);
        List<Language> langs = lDao.list();

        XmlResponse resp = new XmlResponse(res);
        Element root = resp.getDoc().addElement("languages");
        for (Language l : langs) {
            l.toXmlElement(root);
        }
        return resp;
    }


    /**
     * @param cmd HTTP request parameter map:
     *            <ul>
     *            <li>command=listuilanguages</li>
     *            </ul>
     * @return XML-Response with a list of all UiLanguage objects - or an XML-wrapped error message.
     * @see server.i18n.UiLanguage
     */
    @CinnamonMethod(checkTrigger = "true")
    public Response listUiLanguages(Map<String, String> cmd) {
        UiLanguageDAO lDao = daoFactory.getUiLanguageDAO(em);
        List<UiLanguage> languages = lDao.list();

        XmlResponse resp = new XmlResponse(res);
        Element root = resp.getDoc().addElement("languages");
        for (UiLanguage l : languages) {
            l.toXmlElement(root);
        }
        return resp;
    }

    /**
     * @param cmd HTTP request parameter map:
     *            <ul>
     *            <li>command=listmessages</li>
     *            </ul>
     * @return XML-Response:
     *         XML-Response with a list of all Message objects - or an XML-wrapped error message.
     */
    @CinnamonMethod(checkTrigger = "true")
    public Response listMessages(Map<String, String> cmd) {
        MessageDAO mDao = daoFactory.getMessageDAO(em);
        List<Message> msgs = mDao.list();

        XmlResponse resp = new XmlResponse(res);
        Element root = resp.getDoc().addElement("messages");
        for (Message msg : msgs) {
            msg.toXmlElement(root);
        }
        return resp;
    }


    /**
     * The createrelation command links the objects specified by leftid and rightid
     * with a relation of the type specified by name. If the relation already exists,
     * it will not create a new one but return the existing relation.
     *
     * @param cmd HTTP request parameter map:
     *            <ul>
     *            <li>command=createrelation</li>
     *            <li>name=name of the relation type</li>
     *            <li>leftid=id of "left object"</li>
     *            <li>rightid=id of "right object"</li>
     *            <li>[metadata]= optional metadata in XML format, defaults to {@code <meta/>}</li>
     *            <li>ticket=session ticket</li>
     *            </ul>
     * @return XML response with format:
     *         <pre>
     *          {@code
     *                            <relations>
     *                          <relation>
     *                              <id>123</id>
     *                              <leftId>true</leftId>
     *                              <rightId>false</rightId>
     *                              <metadata>....</metadata>
     *                              <type>name of type</type>
     *                         </relations>
     *                         }
     *                         </pre>
     */
    @CinnamonMethod(checkTrigger = "true")
    public Response createRelation(Map<String, String> cmd) {
        Relation rel = new Relation(cmd);
        RelationDAO relationDAO = daoFactory.getRelationDAO(em);
        Relation relation =
                relationDAO.findOrCreateRelation(rel.getType(), rel.getLeft(), rel.getRight(), rel.getMetadata());

        /*
         * Update relations, because it is possible that some
         * other process has changed the OSDs while the user was busy
         * selecting the new relation type or that the client
         * has used the wrong versions.
         */
        Relation.updateRelations(rel.getLeft());
        Relation.updateRelations(rel.getRight());

        XmlResponse resp = new XmlResponse(res);
        Element root = resp.getDoc().addElement("relations");
        relation.toXmlElement(root, true);

        log.debug("Created relation as XML:" + resp.getDoc().asXML());
        return resp;
    }

    /**
     * The delete command deletes the object in the repository with the given id. This operation
     * cascades over related objects, unless they are protected by the relationtype.
     *
     * @param cmd HTTP request parameter map:
     *            <ul>
     *            <li>command=delete</li>
     *            <li>id=object id</li>
     *            <li>ticket=session ticket</li>
     *            </ul>
     * @return XML-Response:
     *         <pre>
     *                         {@code
     *                         	<success>success.delete.object</success>
     *                         } </pre> if successful, an XML-error-node if unsuccessful.
     *
     *         <h2>Needed permissions</h2>
     *         DELETE_OBJECT
     */
    @CinnamonMethod(checkTrigger = "true")
    public Response delete(Map<String, String> cmd) {
        Long id = ParamParser.parseLong(cmd.get("id"), "error.param.id");
        ObjectSystemDataDAO osdDao = daoFactory.getObjectSystemDataDAO(em);
        ObjectSystemData osd = osdDao.get(id);
        log.debug("before validate.");
        (new Validator(user)).validateDelete(osd);

        try {
            log.debug("### delete object: "+id+" ###");
            osdDao.delete(id);
        } catch (Exception e) {
            throw new CinnamonException(e);
        }

        XmlResponse resp = new XmlResponse(res);
        resp.addTextNode("success", "success.delete.object");
        return resp;
    }

    /**
     * The deleteAllVersions command deletes all versions of an object.
     * This operation cascades over related objects,
     * unless they are protected by the relationtype.
     * <br/>
     * <p><i>Note</i>:
     * This command relies on a database with ascending object ids.
     * (Meaning that the HQL query will order results by id and assume that the lower an id
     * the older the object is)</p>
     *
     * @param cmd HTTP request parameter map:
     *            <ul>
     *            <li>command=deleteallversions</li>
     *            <li>id=object id</li>
     *            <li>ticket=session ticket</li>
     *            </ul>
     * @return a HTTP response containing
     *         <pre><success/></pre> if successful, an XML-error-node if unsuccessful.
     */
    @CinnamonMethod(checkTrigger = "true")
    public Response deleteAllVersions(Map<String, String> cmd) {
//		croakUnlessSuperuser("error.must_be_admin", getUser());
        Long id = ParamParser.parseLong(cmd.get("id"), "error.param.id");
        ObjectSystemDataDAO osdDao = daoFactory.getObjectSystemDataDAO(em);
        ObjectSystemData osd = osdDao.get(id);
        if (osd == null) {
            throw new CinnamonException("error.object.not.found");
        }

        List<ObjectSystemData> objectTree = osdDao.findAllVersionsOrderLastToFirst(osd);
        // first check all objects if they may be deleted, otherwise an exception will
        // terminate the database operation but not roll back the Lucene index.
        Validator validator = new Validator(user);
        for (ObjectSystemData o : objectTree) {
            validator.validateDelete(osd);
        }

        for (ObjectSystemData o : objectTree) {
            log.debug("Trying to delete: " + o.getId());
            osdDao.delete(o.getId());
            em.flush();
            LuceneBridge lucene = repository.getLuceneBridge();
            lucene.removeObjectFromIndex(o);
        }

        XmlResponse resp = new XmlResponse(res);
        resp.addTextNode("success", "success.delete.all_versions");
        return resp;
    }

    /**
     * Delete an empty folder specified by the id-parameter.
     * <h2>Needed permissions</h2>
     * DELETE_FOLDER
     *
     * @param cmd HTTP request parameter map:
     *            <ul>
     *            <li>command=deletefolder</li>
     *            <li>id=folder id</li>
     *            <li>ticket=session ticket</li>
     *            </ul>
     * @return XML-Response:
     *         {@code
     *         <success>success.delete.folder</success>
     *         }
     */
    @CinnamonMethod(checkTrigger = "true")
    public Response deleteFolder(Map<String, String> cmd) {
        Long id = ParamParser.parseLong(cmd.get("id"), "error.param.id");
        FolderDAO folderDAO = daoFactory.getFolderDAO(em);
        Folder folder = folderDAO.get(id);
        (new Validator(user)).validateDeleteFolder(folder);
        folderDAO.delete(id);
        repository.getLuceneBridge().removeObjectFromIndex(folder);

        XmlResponse resp = new XmlResponse(res);
        resp.addTextNode("success", "success.delete.folder");
        return resp;
    }

    /**
     * Delete a relation
     *
     * @param cmd HTTP request parameter map:
     *            <ul>
     *            <li>command=deleterelation</li>
     *            <li>id=id of relation to delete</li>
     *            <li>ticket=session ticket</li>
     *            </ul>
     * @return XML-Response
     *         {@code
     *         <success>success.delete.relation</success>
     *         }
     */
    @CinnamonMethod(checkTrigger = "true")
    public Response deleteRelation(Map<String, String> cmd) {
        Long id = ParamParser.parseLong(cmd.get("id"), "error.param.id");
        RelationDAO relDao = daoFactory.getRelationDAO(em);
        relDao.delete(id);

        XmlResponse resp = new XmlResponse(res);
        resp.addTextNode("success", "success.delete.relation");
        return resp;
    }

    /**
     * The getsubfolders command retrieves the subfolders of the folder with the given id.
     * It does not recurse into the folder's sub folders.
     * <h2>Needed permissions</h2>
     * BROWSE_FOLDER for each subfolder
     *
     * @param cmd HTTP request parameter map
     *            <ul>
     *            <li>command=getsubfolders</li>
     *            <li>parentid=parent folder id</li>
     *            <li>ticket=session ticket</li>
     *            </ul>
     * @return XML-Response:
     *         XML document with subfolders.
     */
    @CinnamonMethod(checkTrigger = "true")
    public Response getSubfolders(Map<String, String> cmd) {
        Long parentId = ParamParser.parseLong(cmd.get("parentid"), "error.param.id");

        FolderDAO folderDAO = daoFactory.getFolderDAO(em);
        List<Folder> folders = folderDAO.getSubfolders(parentId);

        log.debug("found " + folders.size() + " subfolders");

        XmlResponse resp = new XmlResponse(res);
        Element root = resp.getDoc().addElement("folders");
        Validator val = (new Validator(user));
        for (Folder f : folders) {
            try {
                val.validateGetFolder(f);
            } catch (Exception e) {
                log.debug("", e);
                continue;
            }
            f.toXmlElement(root);
        }
        
        log.debug("Looking for links.");
        LinkService linkService = new LinkService();
        Collection<Link> links = linkService.findLinksIn(folderDAO.get(parentId), LinkType.FOLDER);
        log.debug("Found "+links.size()+" links.");
        for(Link link : links){
            try{
                val.validatePermission(link.getAcl(), PermissionName.BROWSE_FOLDER);
                val.validatePermission(link.getFolder().getAcl(), PermissionName.BROWSE_FOLDER);
            }
            catch (Exception e){
                log.debug("",e);
                continue;
            }
            Element folderNode = link.getFolder().toXmlElement(root);
            linkService.addLinkToElement(link, folderNode);
        }
        return resp;
    }

    /**
     * The getrelations command retrieves a list of relations.
     * Without the "name", leftid and rightid parameters, it lists all relations.
     * If one or both of the ids and / or the name are specified, the results will be filtered accordingly.
     *
     * @param cmd HTTP request parameter map:
     *            <ul>
     *            <li>command=getrelations</li>
     *            <li>name=relation type (optional)</li>
     *            <li>leftid=id of "left object" (optional)</li>
     *            <li>rightid=id of "right object" (optional)</li>
     *            <li>[include_metadata] = optional parameter whether to include or exclude metadata
     *            from the XML response, defaults to 'true'</li>
     *            <li>ticket=session ticket</li>
     *            </ul>
     * @return XML response: the serialized relation objects.
     */
    @CinnamonMethod(checkTrigger = "true")
    public Response getRelations(Map<String, String> cmd) {
        /*
           * Leftid, rightid, name => 8 possible combinations.
           * Until we have a better solution, I will use 8 namedQueries.
           * (Anything is better than concatenation of unverified strings).
           *
           * I do not want to use Hibernate's criteria selection,
           * as that is not yet in JPA
           */
        ObjectSystemDataDAO osdDao = daoFactory.getObjectSystemDataDAO(em);
        ObjectSystemData left = null;
        if (cmd.containsKey("leftid")) {
            Long id = ParamParser.parseLong(cmd.get("leftid"), "error.param.leftid");
            left = osdDao.get(id);
            if (left == null) {
                throw new CinnamonException("error.param.leftid");
            }
        }
        ObjectSystemData right = null;
        if (cmd.containsKey("rightid")) {
            Long id = ParamParser.parseLong(cmd.get("rightid"), "error.param.rightid");
            right = osdDao.get(id);
            if (right == null) {
                throw new CinnamonException("error.param.rightid");
            }
        }

        RelationTypeDAO rtDao = daoFactory.getRelationTypeDAO(em);
        RelationType type = null;
        if (cmd.containsKey("name")) {
            type = rtDao.findByName(cmd.get("name"));
        }

        log.debug("Select and execute the right query.");
        List<Relation> relations;
        RelationDAO relDao = daoFactory.getRelationDAO(em);
        if (type != null) {
            if (left != null && right != null) {
                // type + leftid + rightid
                log.debug("findAllByLeftAndRightAndType");
                relations = relDao.findAllByLeftAndRightAndType(left, right, type);
            } else if (right != null) {
                // type + rightid
                log.debug("findAllByRightAndType");
                relations = relDao.findAllByRightAndType(right, type);
            } else if (left != null) {
                // type + leftid
                log.debug("findAllByLeftAndType");
                relations = relDao.findAllByLeftAndType(left, type);
            } else {
                // type
                log.debug("Find all relations by type - this may take some time.");
                relations = relDao.findAllByType(type);
            }
        } else {
            if (left != null && right != null) {
                // leftid + rightid
                log.debug("findAllByLeftAndRight");
                relations = relDao.findAllByLeftAndRight(left, right);
            } else if (right != null) {
                // rightid
                log.debug("findAllByRight");
                relations = relDao.findAllByRight(right);
            } else if (left != null) {
                // leftid
                log.debug("findAllByLeft");
                relations = relDao.findAllByLeft(left);
            } else {
                // all
                // who in their right mind would do this on a production db?
                log.debug("findAll - this may take a long time!");
                relations = relDao.list();
            }
        }

        Boolean includeMetadata = true;
        if (cmd.containsKey("include_metadata")) {
            includeMetadata = Boolean.parseBoolean(cmd.get("include_metadata"));
        }

        log.debug(String.format("Found %d relations", relations.size()));
        XmlResponse resp = new XmlResponse(res);
        Element root = resp.getDoc().addElement("relations");
        for (Relation r : relations) {
            r.toXmlElement(root, includeMetadata);
        }
        return resp;
    }

    /**
     * The getrelationtypes command retrieves a list of all relation types.
     *
     * @param cmd HTTP request parameter map:
     *            <ul>
     *            <li>command=getrelationtypes</li>
     *            <li>ticket=session ticket</li>
     *            </ul>
     * @return XML-Response:
     *         <pre>
     *                         {@code
     *         	<relationTypes>
     *         		<relationType>
     *         			<id>1</id>
     *         			<name>ExampleRelation</name>
     *         			<description>ExampleRelationDescription</description>
     *         			<rightobjectprotected>true</rightobjectprotected>
     *         			<leftobjectprotected>false</leftobjectprotected>
     *         			<cloneOnLeftCopy>false</cloneOnLeftCopy>
     *         			<cloneOnRightCopy>false</cloneOnRightCopy>
     *         		</relationType>
     *         		<relationType>
     *         			...
     *         		</relationType>
     *         	<relationTypes>
     *         	}
     */
    @CinnamonMethod(checkTrigger = "true")
    public Response getRelationTypes(Map<String, String> cmd) {
        RelationTypeDAO rtDao = daoFactory.getRelationTypeDAO(em);
        List<RelationType> types = rtDao.list();
        XmlResponse resp = new XmlResponse(res);
        Element root = resp.getDoc().addElement("relationTypes");
        for (RelationType type : types) {
            type.toXmlElement(root);
        }
        return resp;
    }

    /**
     * The getobjtypes command retrieves a list of all object types.
     *
     * @param cmd HTTP request parameter map:
     *            <ul>
     *            <li>command=getobjtypes</li>
     *            <li>ticket=session ticket</li>
     *            </ul>
     * @return XML-Response:
     *         List of ObjectTypes as xml-doc.
     */
    @CinnamonMethod(checkTrigger = "true")
    public Response getObjTypes(Map<String, String> cmd) {
        ObjectTypeDAO otDao = daoFactory.getObjectTypeDAO(em);
        List<ObjectType> objectTypes = otDao.list();
        XmlResponse resp = new XmlResponse(res);
        Element root = resp.getDoc().addElement("objectTypes");
        for (ObjectType ot : objectTypes) {
            root.add(ObjectType.asElement("objectType", ot));
        }
        return resp;
    }


    /**
     * The getfolder command retrieves the folder with the given id.
     * <h2>Needed permissions</h2>
     * BROWSE_FOLDER
     *
     * @param cmd HTTP request parameter map:
     *            <ul>
     *            <li>command=getfolder</li>
     *            <li>id=folder id</li>
     *            <li>ticket=session ticket</li>
     *            </ul>
     * @return XML-Response:
     *         An XML document containing the requested folder and its parent folders.
     */
    @CinnamonMethod(checkTrigger = "true")
    public Response getFolder(Map<String, String> cmd) {


        Long id = ParamParser.parseLong(cmd.get("id"), "error.param.id");
        log.debug("Getfolderbyid: " + id);

        FolderDAO folderDao = daoFactory.getFolderDAO(em);
        Folder folder = folderDao.get(id);
        (new Validator(user)).validateGetFolder(folder);
        List<Folder> folderList = new ArrayList<Folder>();
        folderList.add(folder);
        // TODO: permission check on ancestor folders?
        folderList.addAll(folder.getAncestors());

        XmlResponse resp = new XmlResponse(res);
        Element root = resp.getDoc().addElement("folders");
        for (Folder f : folderList) {
            f.toXmlElement(root);
        }

        return resp;
    }

    /**
     * The getfolderbypath command retrieves the folder with the given path.
     * Do not need to specify the root folder in the path parameter, it will be
     * automatically prepended.
     * <h2>Needed permissions</h2>
     * BROWSE_FOLDER (for each individual folder, else it will be filtered.)
     *
     * @param cmd HTTP request parameter map:
     *            <ul>
     *            <li>command=getfolderbypath</li>
     *            <li>path=path in the form /folder1/folder2/...</li>
     *            <li>[autocreate] = true (optional, default:false, will create missing folders if allowed)</li>
     *            <li>ticket=session ticket</li>
     *            </ul>
     * @return XML-Response
     *         <pre>
     *           {@code
     *           <folders>
     *              <folder><id>5</id>...</folder>
     *               ...
     *           </folders>
     *          }
     *          </pre>
     */
    @CinnamonMethod(checkTrigger = "true")
    public Response getFolderByPath(Map<String, String> cmd) {
        String path = cmd.get("path");
        Boolean autoCreate = cmd.containsKey("autocreate") && cmd.get("autocreate").equals("true");

        FolderDAO folderDAO = daoFactory.getFolderDAO(em);
        Validator validator = new Validator(user);
        List<Folder> folderList = folderDAO.findAllByPath(path, autoCreate, validator);
        XmlResponse resp = new XmlResponse(res);
        Element root = resp.getDoc().addElement("folders");
        Validator val = (new Validator(user));
        for (Folder f : folderList) {
            try {
                val.validateGetFolder(f);
            } catch (Exception e) {
                log.debug("", e);
                continue;
            }
            f.toXmlElement(root);
        }
        return resp;
    }

    /**
     * Get one or all formats. To request only one specific format, the client may
     * supply either an id or the name of a format. If both params are set, the id is
     * used.
     *
     * @param cmd HTTP request parameter map:
     *            <ul>
     *            <li>command=getformats</li>
     *            <li>[optional] id=format_id</li>
     *            <li>[optional] name=format_name</li>
     *            <li>ticket=session ticket</li>
     *            </ul>
     * @return XML-Response:
     *         <pre>
     *                         	{@code
     *                         		<formats>
     *                                  <format>...</format>
     *                              </formats>
     *                         	}
     *                         </pre>
     */
    @CinnamonMethod(checkTrigger = "true")
    public Response getFormats(Map<String, String> cmd) {
        FormatDAO formatDao = daoFactory.getFormatDAO(em);

        List<Format> formats = new ArrayList<Format>();
        if (cmd.containsKey("id")) {
            // format by id
            Format f = formatDao.get(cmd.get("id"));
            // add format only if no name has been passed or id and name match
            if (f != null) {
                formats.add(f);
            }
        } else if (cmd.containsKey("name")) {
            Format format = formatDao.findByName(cmd.get("name"));
            if (format != null) {
                formats.add(format);
            }
        } else {
            log.debug("no id or name given");
            formats = formatDao.list();
        }

        log.debug("formats.size() = " + formats.size());
        XmlResponse resp = new XmlResponse(res);
        Element root = resp.getDoc().addElement("formats");
        for (Format f : formats) {
            root.add(Format.asElement("format", f));
        }
        return resp;
    }

    /**
     * Retrieves the content with the given id. The user may then edit the file.
     * <h2>Needed permissions</h2>
     * READ_OBJECT_CONTENT
     *
     * @param cmd HTTP request parameter map:
     *            <ul>
     *            <li>command=getcontent</li>
     *            <li>id=id of object to be retrieved</li>
     *            <li>resultfile=file to store the object to</li>
     *            <li>ticket=session ticket</li>
     *            </ul>
     * @return the raw content of the object as byte stream (MIME-type: binary/octet-stream)
     */
    @CinnamonMethod(checkTrigger = "true")
    public Response getContent(Map<String, String> cmd) {
        Long id = ParamParser.parseLong(cmd.get("id"), "error.param.id");

        ObjectSystemDataDAO osdDAO = daoFactory.getObjectSystemDataDAO(em);
        ObjectSystemData osd = osdDAO.get(id);
        (new Validator(user)).validateGetContent(osd);

        String filename = osd.getFullContentPath(repository.getName());
        if (filename == null) {
            throw new CinnamonException("error.content.not.found");
        }
        File file = new File(filename);
        return new FileResponse(res, filename,
                osd.getContentSize(), file.getName());
    }

    /**
     * The getobjects command retrieves some or all objects in the folder with the given id.
     * <br>
     * The optional versions parameter allows requesting all versions (all),
     * only the newest version in the trunk of the version tree (head) or the newest
     * version including branches (branch).
     * <h2>Needed permissions</h2>
     * BROWSE_OBJECT
     *
     * @param cmd HTTP request parameter map
     *            <ul>
     *            <li>command=getobjects</li>
     *            <li>parentid=parent folder id</li>
     *            <li>versions=all,branch,head (optional, default=head)</li>
     *            <li>ticket=session ticket</li>
     *            </ul>
     * @return XML-Response: List of object data as XML document.
     */
    @CinnamonMethod(checkTrigger = "true")
    public Response getObjects(Map<String, String> cmd) {
        ObjectSystemDataDAO oDao = daoFactory.getObjectSystemDataDAO(em);
        List<ObjectSystemData> results = oDao.getObjectsInFolder(cmd.get("versions"), cmd.get("parentid"));
        Validator val = new Validator(user);
        results = val.filterUnbrowsableObjects(results);
        XmlResponse resp = new XmlResponse(res);
        
        Document doc = ObjectSystemData.generateQueryObjectResultDocument(results);        
        addLinksToObjectQuery(cmd.get("parentid"), doc, val, false);
        resp.setDoc(doc);
        return resp;
    }

    /**
     * The getobjectswithmetadata command retrieves some or all objects in the folder with the given id
     * and returns their metadata and system metadata.
     * The user needs both the browse object permission and the permission to read the metadata.
     * <br>
     * The optional versions parameter allows requesting all versions (all),
     * only the newest version in the trunk of the version tree (head) or the newest
     * version including branches (branch).
     *
     * @param cmd HTTP request parameter map:
     *            <ul>
     *            <li>command=getobjectswithcustommetadata</li>
     *            <li>parentid=parent folder id</li>
     *            <li>[versions]=all,branch,head (optional, default=head)</li>
     *            <li>ticket=session ticket</li>
     *            </ul>
     * @return XML-Response:
     *         List of object data as XML document.
     *         <p/>
     *         <h2>Needed permissions</h2>
     *         <ul>
     *         <li>READ_OBJECT_CUSTOM_METADATA</li>
     *         <li>BROWSE_OBJECT</li>
     *         </ul>
     */
    @CinnamonMethod(checkTrigger = "true")
    public Response getObjectsWithCustomMetadata(Map<String, String> cmd) {
        ObjectSystemDataDAO oDao = daoFactory.getObjectSystemDataDAO(em);
        List<ObjectSystemData> results = oDao.getObjectsInFolder(cmd.get("versions"), cmd.get("parentid"));
        Validator val = new Validator(user);
        results = val.filterUnbrowsableObjects(results);
        val = new Validator(user);
        results = val.filterForCustomMetadata(results);
        XmlResponse resp = new XmlResponse(res);        
        Document doc = ObjectSystemData.generateQueryObjectResultDocument(results, true);        
        addLinksToObjectQuery(cmd.get("parentid"), doc, val, true);
        resp.setDoc(doc);
        return resp;
    }

    void addLinksToObjectQuery(String parentId, Document doc, Validator val, Boolean withMetadata){
        FolderDAO fDao = daoFactory.getFolderDAO(em);
        Folder parent = fDao.get(parentId);
        LinkService linkService = new LinkService();
        Element root = doc.getRootElement();
        Collection<Link> links = linkService.findLinksIn(parent, LinkType.OBJECT);
        log.debug("Found " + links.size() + " links.");
        for (Link link : links) {
            try {
                val.validatePermission(link.getAcl(), PermissionName.BROWSE_OBJECT);
                val.validatePermission(link.getOsd().getAcl(), PermissionName.BROWSE_OBJECT);
            } catch (Exception e) {
                log.debug("", e);
                continue;
            }
            Element osdNode = link.getOsd().toXmlElement(root);
            if(withMetadata){
                osdNode.add(ParamParser.parseXml(link.getOsd().getMetadata(), null));
            }
            linkService.addLinkToElement(link, osdNode);
        }   
    }
    
    /**
     * Set the current user's password. The minimum length of the password may be defined
     * in the Cinnamon configuration file in the element minimumPasswordLength. The default is 4.
     *
     * @param cmd HTTP request parameter map:
     *            <ul>
     *            <li>command=setpassword</li>
     *            <li>password=the new password</li>
     *            <li>ticket=session ticket</li>
     *            </ul>
     * @return XML-Response:
     *         <pre>
     *                         {@code
     *                         <success>success.set.password</success>
     *                         }
     *                         </pre> or an XML error node on failure.
     */
    @CinnamonMethod(checkTrigger = "true")
    public Response setPassword(Map<String, String> cmd) {
        // default minLength is 4 for historical reasons.
        //Nothing prevents the user from increasing this (except natural inertia).
        Integer minLength = Integer.parseInt(conf.getField("minimumPasswordLength", "4"));

        String password = cmd.get("password");
        if (password.length() < minLength) {
            throw new CinnamonException("error.password.short", minLength.toString());
        }
        user.setPwd(password);

        XmlResponse resp = new XmlResponse(res);
        resp.addTextNode("success", "success.set.password");
        return resp;
    }

    /**
     * Set a user's password by web form.
     * The minimum length of the password may be defined
     * in the Cinnamon configuration file in the element minimumPasswordLength. The default is 4.
     *
     * @param cmd HTTP request parameter map:
     *            <ul>
     *            <li>command=setpassword</li>
     *            <li>password=the new password</li>
     *            <li>ticket=session ticket</li>
     *            </ul>
     * @return XML-Response:
     *         <pre>
     *                         {@code
     *                         <success>success.set.password</success>
     *                         }
     *                         </pre> or an XML error node on failure.
     * @throws IOException if file for mail template cannot be read.
     */
    @CinnamonMethod(checkTrigger = "true")
    public Response setPasswordByForm(Map<String, String> cmd) throws IOException {
        // default minLength is 4 for historical reasons.
        //Nothing prevents the user from increasing this (except natural inertia).
        Integer minLength = Integer.parseInt(conf.getField("minimumPasswordLength", "4"));

        String password = cmd.get("password");
        if (password.length() < minLength) {
            throw new CinnamonException("error.password.short", minLength.toString());
        }
        String userName = cmd.get("login");
        UserDAO uDao = daoFactory.getUserDAO(em);
        user = uDao.findByName(userName);
        user.setPwd(password);
        user.clearToken();

        HtmlResponse resp = new HtmlResponse(res);
        TemplateProvider tp = new TemplateProvider();
        String language = user.getLanguage() != null ? user.getLanguage().getIsoCode() : "und";
        String successMessage =
                ContentReader.readFileAsString(tp.getPathToTemplates() +
                        File.separator + language + File.separator + "reset_password_success.vt");
        resp.setContent(successMessage);
        return resp;
    }

    /**
     * The getMeta command retrieves the metadata of the specified object.
     * <h2>Needed permissions</h2>
     * READ_OBJECT_CUSTOM_METADATA
     *
     * @param cmd HTTP request parameter map:
     *            <ul>
     *            <li>command=getmeta</li>
     *            <li>id=object id</li>
     *            <li>ticket=session ticket</li>
     *            <li>[metasets]=optional parameter: comma-separated list of metasetType names. Defaults to
     *            returning the content of all metasets that are referenced by this object.
     *            </li>
     *            </ul>
     * @return XML-Response:
     *         The metadata of the specified object.
     */
    @CinnamonMethod(checkTrigger = "true")
    public Response getMeta(Map<String, String> cmd) {
        Long id = ParamParser.parseLong(cmd.get("id"), "error.param.id");
        ObjectSystemDataDAO osdDAO = daoFactory.getObjectSystemDataDAO(em);
        ObjectSystemData data = osdDAO.get(id);
        (new Validator(user)).validateGetMeta(data);
        
        if(cmd.containsKey("metasets")){
            List<String> metasetNames = Arrays.asList(cmd.get("metasets").split(","));
            return new TextResponse(res, data.getMetadata(metasetNames));
        }
        return new TextResponse(res, data.getMetadata());
    }

    /**
     * The getConfigEntry command retrieves the config entry with the given name.
     * Access to config entries is limited to superusers unless the XML config entry
     * contains the element {@code <isPublic>true</isPublic> }
     *
     * @param cmd HTTP request parameter map:
     *            <ul>
     *            <li>command=getconfigentry</li>
     *            <li>name=name of the config entry</li>
     *            <li>ticket=session ticket</li>
     *            </ul>
     * @return XML-Response:
     *         The metadata of the specified object.
     */
    @CinnamonMethod(checkTrigger = "true")
    public Response getConfigEntry(Map<String, String> cmd) {
        String name = cmd.get("name");
        ConfigEntryDAO ceDao = daoFactory.getConfigEntryDAO(em);
        ConfigEntry configEntry = ceDao.findByName(name);
        if(configEntry == null){
            throw new CinnamonException("error.missing.config");
        }
        Document doc = ParamParser.parseXmlToDocument(configEntry.getConfig());
        if(user.verifySuperuserStatus(em) || doc.selectSingleNode("//isPublic[text()='true']") != null ){
            return new XmlResponse(res, doc);
        }
        else{
            throw new CinnamonException("error.access.denied");
        }
    }

    /**
     * The setConfigEntry command sets the config entry with the given name.
     * This command is only available to superusers. To allow non-superusers to
     * view / download config entries, add the element {@code <isPublic>true</isPublic> }
     * to the XML of the config.
     *
     * @param cmd HTTP request parameter map:
     *            <ul>
     *            <li>command=setconfigentry</li>
     *            <li>name=name of the config entry - if it exists, replace the current content.</li>
     *            <li>config=XML content of the config entry.</li>
     *            <li>ticket=session ticket</li>
     *            </ul>
     * @return XML-Response:
     *         <pre>{@code
     *         <configEntryId>$configEntryId</configEntryId>
     *         }</pre>
     */
    @CinnamonMethod(checkTrigger = "true")
    public Response setConfigEntry(Map<String, String> cmd) {
        String name = cmd.get("name");
        if(name == null || name.trim().length() == 0){
            throw new CinnamonException("error.param.name");
        }
        if(! user.verifySuperuserStatus(em) ){
            throw new CinnamonException("error.access.denied");
        }
        
        Document config = ParamParser.parseXmlToDocument(cmd.get("config"), "error.parse.config");
        ConfigEntryDAO ceDao = daoFactory.getConfigEntryDAO(em);
        ConfigEntry configEntry = ceDao.findByName(name);
        if(configEntry == null){
            configEntry = new ConfigEntry();
            configEntry.setName(name);
            configEntry.setConfig(config.asXML());
            ceDao.makePersistent(configEntry);
        }
        else{
            configEntry.setConfig(config.asXML());
        }
        return new XmlResponse(res, "<configEntryId>"+configEntry.getId()+"</configEntryId>");
    }

    /**
     * The getFolderMeta command retrieves the metadata of the specified folder.
     * <h2>Needed permissions</h2>
     * READ_OBJECT_CUSTOM_METADATA
     *
     * @param cmd HTTP request parameter map:
     *            <ul>
     *            <li>command=getfoldermeta</li>
     *            <li>id=folder id</li>
     *            <li>ticket=session ticket</li>
     *            </ul>
     * @return XML-Response:
     *         The metadata of the specified folder or an empty string.
     */
    @CinnamonMethod(checkTrigger = "true")
    public Response getFolderMeta(Map<String, String> cmd) {

        FolderDAO folderDao = daoFactory.getFolderDAO(em);
        Folder folder = folderDao.get(cmd.get("id"));
        (new Validator(user)).validateGetFolderMeta(folder);
        Document doc = ParamParser.parseXmlToDocument(folder.getMetadata(),
                "error.parse.xml");
        return new XmlResponse(res, doc);
    }


    /**
     * The getsysmeta command fetches one of the system attributes of an object specified
     * by the "parameter" value. The following parameters can be retrieved:*
     *
     * @param cmd HTTP request parameter map:
     *            <ul>
     *            <li>command=getsysmeta</li>
     *            <li>id=object id</li>
     *            <li>parameter=parameter to be read</li>
     *            <li>ticket=session ticket</li>
     *            </ul>
     *            <h2>Possible values of "parameter"</h2>
     *            <ul>
     *            <li>     preid </li>
     *            <li>     locked </li>
     *            <li>     owner </li>
     *            <li>     contentsize </li>
     *            <li>     cntformat </li>
     *            <li>     procstate </li>
     *            <li>     creator </li>
     *            <li>     created </li>
     *            <li>		language_id </li>
     *            <li>     modifier </li>
     *            <li>		modifier_id </li>
     *            <li>     modified </li>
     *            <li>     version </li>
     *            <li>     rootid </li>
     *            <li>     objtype </li>
     *            <li>     objtype_id </li>
     *            <li>		acl_id</li>
     *            </ul>
     *            <p/>
     *            <h2>Needed permissions</h2>
     *            READ_OBJECT_SYS_META oder BROWSE_FOLDER
     * @return XML-Response: {@code <sysMetaValue>$value</sysMetaValue>}
     *         If a null value is retrieved, an xml-error-doc is returned with the message:
     *         "error.result_value_is_null"
     */
    @CinnamonMethod(checkTrigger = "true")
    public Response getSysMeta(Map<String, String> cmd) {
        String param = cmd.get("parameter");
        String value = null;
        // TODO: translate owner, modifier, objtype etc. to corresponding id
        List<String> validParams = Arrays.asList("parentid", "preid",
                "name", "locked", "owner", "contentsize", "cntformat",
                "procstate", "creator", "created", "modifier",
                "modified", "acl_id", "version", "rootid",
                "objtype", "appname",
                "language_id", "objtype_id",
                "modifier_id", "owner_id", "creator_id");
        if (!validParams.contains(param)) {
            throw new CinnamonException("Parameter "
                    + param + " can not be read on objects.");
        }

        /*
           * Note: if we would just return the OSDs fields as strings,
           * we could use osd.toXML() and use the param in an xpath statement
           * to return (//$paramname).toString().
           * But
           * 1. the params to getSysMeta are not named like the fields of OSD
           * 2. the client sometimes just needs the owner's name, not the
           * owner's id.
           * Note2: in Groovy (or Perl) it would mostly just be something like
           * value = osd."$param". Alas, the disadvantages of type safe languages.
           */
        //TODO: should this if-else-orgy be placed into server.data.OSD?
        ObjectSystemDataDAO osdDao = daoFactory.getObjectSystemDataDAO(em);
        ObjectSystemData osd = osdDao.get(cmd.get("id"));
        (new Validator(user)).validateGetSysMeta(osd);

        if (param.equals("objtype")) {
            //				value = String.valueOf(osd.getType().getId());
            if (osd.getType() != null) {
                value = osd.getType().getName();
            }
        } else if (param.equals("objtype_id")) {
            if (osd.getType() != null) {
                value = String.valueOf(osd.getType().getId());
            }
        } else if (param.equals("owner_id")) {
            value = getUserIdAsString(osd.getOwner());
        } else if (param.equals("modifier_id")) {
            value = getUserIdAsString(osd.getModifier());
        } else if (param.equals("creator_id")) {
            value = getUserIdAsString(osd.getCreator());
        } else if (param.equals("owner")) {
            value = getUserName(osd.getOwner());
        } else if (param.equals("modifier")) {
            value = getUserName(osd.getModifier());
        } else if (param.equals("creator")) {
            value = getUserName(osd.getCreator());
        } else if (param.equals("parentid")) {
            value = String.valueOf(osd.getParent().getId());
        } else if (param.equals("objtype")) {
            value = osd.getType().getName();
        } else if (param.equals("preid")) {
            if (osd.getPredecessor() != null) {
                value = String.valueOf(osd.getPredecessor().getId());
            }
        } else if (param.equals("name")) {
            value = osd.getName();
        } else if (param.equals("appname")) {
            value = osd.getAppName();
        } else if (param.equals("contentsize")) {
            value = String.valueOf(osd.getContentSize());
        } else if (param.equals("cntformat")) {
            if (osd.getFormat() != null) {
                value = String.valueOf(osd.getFormat().getId());
            }
        } else if (param.equals("procstate")) {
            value = osd.getProcstate();
        } else if (param.equals("locked")) {
            value = getUserIdAsString(osd.getLocked_by());
        } else if (param.equals("modified")) {
            value = ParamParser.dateToIsoString(osd.getModified());
        } else if (param.equals("created")) {
            value = ParamParser.dateToIsoString(osd.getCreated());
        } else if (param.equals("acl_id")) {
            value = String.valueOf(osd.getAcl().getId());
        } else if (param.equals("version")) {
            value = osd.getVersion();
        } else if (param.equals("rootid")) {
            if (osd.getRoot() != null) {
                value = String.valueOf(osd.getRoot().getId());
            }
        } else if (param.equals("language_id")) {
            value = String.valueOf(osd.getLanguage().getId());
        }

        if (value == null) {
            throw new CinnamonException("error.result_value_is_null");
        }
        XmlResponse resp = new XmlResponse(res);
        resp.addTextNode("sysMetaValue", value);
        return resp;
    }

    /**
     * Utility method: Returns a user's name or null if the user is null.
     *
     * @param user the user whose name you want.
     * @return String with user's name or null
     */
    private String getUserName(User user) {
        if (user != null) {
            return user.getName();
        } else {
            return null;
        }
    }

    /**
     * Utility method: Returns a user's id as a String or null if user is null.
     *
     * @param user the user whose Id you want to convert to a string
     * @return String with user's id or null if the user parameter is null.
     */
    private String getUserIdAsString(User user) {
        if (user != null) {
            return String.valueOf(user.getId());
        }
        return null;
    }

    /**
     * The setmeta command sets the metadata to the specified value.
     * If no metadata parameter is specified, the metadata is set to {@code <meta />}.
     * <h2>Needed permissions</h2>
     * WRITE_OBJECT_CUSTOM_METADATA
     *
     * @param cmd HTTP request parameter map:
     *            <ul>
     *            <li>command=setmeta</li>
     *            <li>id=object id</li>
     *            <li>metadata=metadata to be set</li>
     *            <li>[write_policy]= optional write policy for metasets. Allowed values:
     *            write|ignore|branch - default: branch
     *            </li>
     *            <li>ticket=session ticket</li>
     *            </ul>
     * @return {@code
     *         <cinnamon>
     *         <success>success.set.metadata</success>
     *         </cinnamon>
     *         }
     *         if successful, xml-error-doc if unsuccessful.
     *         The response document may include additional elements as children of the root element
     *         (for example, {@code <warnings />}
     */
    @CinnamonMethod(checkTrigger = "true")
    public Response setMeta(Map<String, String> cmd) {
        Long id = ParamParser.parseLong(cmd.get("id"), "error.param.id");
        ObjectSystemDataDAO osdDAO = daoFactory.getObjectSystemDataDAO(em);
        ObjectSystemData osd = osdDAO.get(id);
        (new Validator(user)).validateSetMeta(osd);

        String metadata = cmd.get("metadata");
        metadata = metadata == null ? "" : metadata.trim();

        osd.setMetadata(metadata);
        osd.updateAccess(getUser());

        XmlResponse resp = new XmlResponse(res);
        Element root = resp.getDoc().addElement("cinnamon");
        root.addElement("success").addText("success.set.metadata");
        return resp;
    }


    /**
     * The echo command does nothing, except returning the input xml.
     * Its value lies in the fact that you can use it
     * in combination with change triggers, so for example if you have a
     * change trigger that updates an object's metadata, you can use echo
     * for migration purposes - wire the trigger to this command,
     * upload the metadata the change trigger expects as its input and it
     * should update the object accordingly (provided it accepts "echo" as
     * a valid command).
     *
     * @param cmd HTTP request parameter map:
     *            <ul>
     *            <li>command=echo</li>
     *            <li>[id] = optional id, must be a valid integer value</li>
     *            <li>xml= input xml - must be well formed</li>
     *            <li>ticket=session ticket</li>
     *            </ul>
     * @return the xml content - or an error message if the input xml was not
     *         well formed.
     */
    @CinnamonMethod(checkTrigger = "true")
    public Response echo(Map<String, String> cmd) {
        if (cmd.containsKey("id")) {
            ParamParser.parseLong(cmd.get("id"), "error.param.id");
        }
        String xml = cmd.get("xml");
        Document doc = ParamParser.parseXmlToDocument(xml, "error.parse.xml");
        return new XmlResponse(res, doc);
    }


    /**
     * The setsysmeta command sets one of the system attributes of an object or folder
     * to the specified value. If an id parameter is specified, the metadata is applied to the object
     * with the specified id. If a folderid parameter is specified, the metadata is applied to the folder
     * with the specified id. Either an id or a folderid must be specified,
     * but not both or none. Folders do not have all the metadata of objects.
     * The following parameters can be set:
     * <ul>
     * <li>     parentid (= id of folder in which the object or folder resides)</li>
     * <li>     name</li>
     * <li>     owner  (=id of the owner)</li>
     * <li>     procstate </li>
     * <li>     acl_id (= id of an ACL)</li>
     * <li>     objtype  (currently, this parameter is the _name_ of an objtype, NOT an id!)</li>
     * <li>     language_id (= id of a language)</li>
     * </ul>
     * <p/>
     * <h2>Needed permissions</h2>
     * <ul>
     * <li>LOCK und (WRITE_OBJECT_SYS_METADATA oder EDIT_FOLDER)</li>
     * <li>for aclId: SET_ACL</li>
     * <li>for parent_id: MOVE</li>
     * <ul>
     *
     * @param cmd HTTP request parameter map:
     *            <ul>
     *            <li>command=setsysmeta</li>
     *            <li>id=object id</li>
     *            <li>parameter= parameter to be set</li>
     *            <li>value=value to assign</li>
     *            <li>ticket=session ticket</li>
     *            </ul>
     * @return XML-Response:
     *         {@code
     *         <success>success.set.sys_meta</success>
     *         }
     * @see server.global.PermissionName#MOVE
     */
    // TODO: owner should be an id-param, not the login name of a user!
    @CinnamonMethod(checkTrigger = "true")
    public Response setSysMeta(Map<String, String> cmd) {
        String parameter = cmd.get("parameter");
        String value = cmd.get("value");

        if (value == null) {
            value = "";
        }
        Validator validator = new Validator(user);

        FolderDAO folderDAO = daoFactory.getFolderDAO(em);
        AclDAO aclDAO = daoFactory.getAclDAO(em);
        Long id = ParamParser.parseLong(cmd.get("id"), "error.param.id");
        ObjectSystemDataDAO osdDAO = daoFactory.getObjectSystemDataDAO(em);
        ObjectSystemData osd = osdDAO.get(id);
        if (osd == null) {
            // TODO: parametrize correctly
            throw new CinnamonException("error.object.not.found");
        }

        if (!parameters.contains(parameter)) {
            throw new CinnamonException("Parameter " + parameter + " is invalid on objects.");
        }

        if (parameter.equals("objtype")) {
            validator.validateSetSysMeta(osd);
            ObjectTypeDAO objectTypeDAO = daoFactory.getObjectTypeDAO(em);
            log.debug(String.format("ot = get(%s)", value));
            ObjectType type = objectTypeDAO.findByName(value);
            if (type == null) {
                throw new CinnamonException("error.param.objtype");
            }
            osd.setType(type);
        }

        if (parameter.equals("parentid")) {
            Folder f = folderDAO.get(value);
            if (f == null) {
                // TODO: parametrize correctly
                throw new CinnamonException("error.parent_folder.not_found");
//				throw new CinnamonException( "Parentfolder with id "+value+" was not found.");				
            }
            validator.validateMoveObject(osd, f);
            osd.setParent(f);
        } else if (parameter.equals("owner")) {
            validator.validateSetSysMeta(osd);
            User owner;
            try {
                UserDAO userDAO = daoFactory.getUserDAO(em);
                owner = userDAO.get(value);
            } catch (Exception e) {
                // TODO: parametrize correctly
                throw new CinnamonException("error.user.not_found");
            }
            osd.setOwner(owner);
        } else if (parameter.equals("language_id")) {
            validator.validateSetSysMeta(osd);
            LanguageDAO langDao = daoFactory.getLanguageDAO(em);
            Language lang = langDao.get(value);
            if (lang == null) {
                throw new CinnamonException("error.object.not.found");
            }
            osd.setLanguage(lang);
        }

        // params left are: name, procstate, permissionid, appname [undocumented?]
        // In want of a better method, for now just set them via if-else...
        else if (parameter.equals("name")) {
            validator.validateSetSysMeta(osd);
            osd.setName(value);
        } else if (parameter.equals("procstate")) {
            validator.validateSetSysMeta(osd);
            osd.setProcstate(value);
        } else if (parameter.equals("appname")) {
            validator.validateSetSysMeta(osd);
            osd.setAppName(value);
        } else if (parameter.equals("acl_id")) {
            validator.validateSetObjectAcl(osd);
            Long aclId = ParamParser.parseLong(value, "error.param.acl_id");
            Acl acl = aclDAO.get(aclId);
            if (acl == null) {
                throw new CinnamonException("error.acl.not_found");
            }
            osd.setAcl(acl);
        }
        osd.updateAccess(getUser());

        XmlResponse resp = new XmlResponse(res);
        resp.addTextNode("success", "success.set.sys_meta");
        return resp;
    }

    /**
     * The lock command places a lock for the session owner on an object.
     * <h2>Needed permissions</h2>
     * LOCK
     *
     * @param cmd HTTP request parameter map:
     *            <ul>
     *            <li>command=lock</li>
     *            <li>id = object id</li>
     *            <li>ticket=session ticket</li>
     *            </ul>
     * @return XML-Response:
     *         {@code
     *         <success>success.object.lock</success>
     *         }
     */
    @CinnamonMethod(checkTrigger = "true")
    public Response lock(Map<String, String> cmd) {
        ObjectSystemDataDAO osdDAO = daoFactory.getObjectSystemDataDAO(em);
        ObjectSystemData osd = osdDAO.get(cmd.get("id"));
        (new Validator(user)).validateLock(osd, user);
        User user = getUser();
        osd.setLocked_by(user);
        log.debug("lock - done.");

        XmlResponse resp = new XmlResponse(res);
        resp.addTextNode("success", "success.object.lock");
        return resp;

    }

    /**
     * The unlock command removes a lock from an object.
     *
     * @param cmd HTTP request parameter map
     *            <ul>
     *            <li>command=unlock</li>
     *            <li>id=object id</li>
     *            <li>ticket=session ticket</li>
     *            </ul>
     * @return XML-Response
     *         {@code
     *         <success>success.object.lock</success>
     *         }
     */
    @CinnamonMethod(checkTrigger = "true")
    public Response unlock(Map<String, String> cmd) {
        ObjectSystemDataDAO osdDAO = daoFactory.getObjectSystemDataDAO(em);
        ObjectSystemData osd = osdDAO.get(cmd.get("id"));
        (new Validator(user)).validateUnlock(osd);
        osd.setLocked_by(null);
        log.debug("unlock - done");
        XmlResponse resp = new XmlResponse(res);
        resp.addTextNode("success", "success.object.unlock");
        return resp;
    }

    /**
     * The setcontent command replaces the content of an object in the repository.
     * If a file is specified, the format must also be specified. The value in the name column
     * of the formats table must be used.
     * <br>
     * If no file parameter is specified, the content is removed.
     * The setcontent command can be used to add content later.
     * <h2>Needed permissions</h2>
     * WRITE_OBJECT_CONTENT
     *
     * @param cmd HTTP request parameter map:
     *            <ul>
     *            <li>command=setcontent</li>
     *            <li>file=path to file for upload (optional)</li>
     *            <li>format=content format as formats.name value (optional)</li>
     *            <li>id=object id</li>
     *            <li>ticket=session ticket</li>
     *            </ul>
     * @return XML-Response:
     *         {@code
     *         <success>success.set.content</success>
     *         }
     *         if successful, xml-error-doc if unsuccessful.
     * @throws IOException if file upload fails.
     */
    @CinnamonMethod(checkTrigger = "true")
    public Response setContent(Map<String, Object> cmd) throws IOException {

        ObjectSystemDataDAO osdDao = daoFactory.getObjectSystemDataDAO(em);
        ObjectSystemData osd = osdDao.get((String) cmd.get("id"));
        if (osd == null) {
            throw new CinnamonException("error.object.not.found");
        }
        (new Validator(user)).validateSetContent(osd);
        String repositoryName = repository.getName();
        osd.deleteContent(repositoryName);// TODO: this action is not recoverable.
        String oldContentPath = osd.getContentPath() != null ? osd.getContentPath() : "";
        if (cmd.containsKey("file")) {
            String format = (String) cmd.get("format");
            log.debug("file_format: " + format);
            String contentPath = ContentStore.upload((UploadedFile) cmd.get("file"), repositoryName);
            if (contentPath.length() == 0) {
                throw new CinnamonException("error.store.upload");
            }
            // TODO: should not query format type by name - use id instead!
            osd.setContentPathAndFormat(contentPath, format, repositoryName);
            new TikaParser().parse(osd, repository.getName());
        }
        osd.updateAccess(getUser());
        
        // audit trail:
        if(! oldContentPath.equals(osd.getContentPath())){       
            AuditService auditService = new AuditService(repository.getAuditConnection());
            String logMessage = "setContent";
            auditService.createLogEvent(osd, user, "contentPath", oldContentPath, osd.getContentPath(), logMessage);
        }
        XmlResponse resp = new XmlResponse(res);
        resp.addTextNode("success", "success.set.content");
        return resp;
    }

    /**
     * The version command creates an object in the repository with the given name,
     * and links it with the preid object as a new version of the latter. The name,
     * metadata and parentid parameters are optional.
     * If they are unspecified, they are copied from preid. It is possible, but unusual
     * to have different object versions in different folders and with different names.
     * If a file is specified, the format must also be specified.
     * The value in the name column of the formats table must be used.
     * The id of the newly created object is returned.
     * <br>
     * If no file parameter is specified, an object without content is created.
     * The setcontent command can be used to add content later.
     * <br>
     * The metadata must be specified in one row.
     *
     * @param cmd HTTP request parameter map:
     *            <ul>
     *            <li>command=version</li>
     *            <li>preid=predecessor id</li>
     *            <li>metadata=xml metadata (optional)</li>
     *            <li>name=object name (optional)</li>
     *            <li>file=path to file for upload (optional)</li>
     *            <li>format=content format as formats.name value (optional)</li>
     *            <li>parentid=parent folder id (optional)</li>
     *            <li>ticket=session ticket</li>
     *            </ul>
     * @return XML-Response:
     *         {@code
     *         <objectId>$id</objectId>
     *         }
     *         Id of new version
     * @throws IOException if the file upload fails.
     */
    @CinnamonMethod(checkTrigger = "true")
    public Response version(Map<String, Object> cmd) throws IOException {
        log.debug("create new OSD");
        ObjectSystemData osd = new ObjectSystemData(cmd, getUser(), true);
        ObjectSystemData pre = osd.getPredecessor();
        if (pre == null) {
            throw new CinnamonException("error.predecessor.not_found");
        }
        log.debug("validateVersion");
        (new Validator(user)).validateVersion(pre);

        if (cmd.containsKey("file")) {
            String contentPath = ContentStore.upload((UploadedFile) cmd.get("file"),
                    repository.getName());
            osd.setContentPath(contentPath, repository.getName());
            if (osd.getContentPath() == null ||
                    osd.getContentPath().length() == 0) {
                throw new CinnamonException("error.storing.upload");
            }
            new TikaParser().parse(osd, repository.getName());
        }

        // index new object:
        log.debug("makePersistent");
        ObjectSystemDataDAO osdDao = daoFactory.getObjectSystemDataDAO(em);
        osdDao.makePersistent(osd);

        // execute the new LifeCycleState if necessary.
        if (osd.getState() != null) {
            osd.getState().enterState(osd, osd.getState(), repository, user);
        }

        // audit trail:
        AuditService auditService = new AuditService(repository.getAuditConnection());
        String logMessage = "new.version";
        LogEvent event = auditService.createLogEvent(osd, user, "version", pre.getVersion(), osd.getVersion(), logMessage);
        auditService.insertLogEvent(event);
        
        XmlResponse resp = new XmlResponse(res);
        resp.addTextNode("objectId", String.valueOf(osd.getId()));
        return resp;
    }

    /**
     * The disconnect command deletes the session specified by its ticket from the session database.
     *
     * @param cmd HTTP request parameter map:
     *            <ul>
     *            <li>command=disconnect</li>
     *            <li>ticket=session ticket</li>
     *            </ul>
     * @return XML-Response:
     *         {@code
     *         <success>success.disconnect</success>
     *         }
     */
    @CinnamonMethod(checkTrigger = "true")
    public Response disconnect(Map<String, String> cmd) {
        SessionDAO sessionDao = daoFactory.getSessionDAO(em);
        Session s = sessionDao.findByTicket(cmd.get("ticket"));
        if(s != null){
            sessionDao.delete(s);
        }
        XmlResponse resp = new XmlResponse(res);
        resp.addTextNode("success", "success.disconnect");
        return resp;
    }

    /**
     * Set name, parent folder, metadata, owner and/or ACL of a folder.
     *
     * @param cmd Map with Key/Value-Pairs<br>
     *            <ul>
     *            <li>id = ID of the folder (integer)</li>
     *            <li>[parentid] = new parent id</li>
     *            <li>[name]= new name</li>
     *            <li>[aclid] = id of new ACL</li>
     *            <li>[ownerid] = id of new owner</li>
     *            <li>[metadata] = new metadata for this folder.</li>
     *            <li>[typeid] = id of new FolderType.</li>
     *            <li>ticket=session ticket (at least, if this method is called via http)</li>
     *            </ul>
     * @return XML-Response:
     *         {@code
     *         <success>success.update.folder</success>
     *         }
     */
    @CinnamonMethod(checkTrigger = "true")
    public Response updateFolder(Map<String, String> cmd) {
        Long id = ParamParser.parseLong(cmd.get("id"), "error.param.id");
        FolderDAO folderDao = daoFactory.getFolderDAO(em);
        Folder folder = folderDao.get(id);

        (new Validator(user)).validateUpdateFolder(cmd, folder);
        folder = folderDao.update(id, cmd);
        XmlResponse resp = new XmlResponse(res);
        resp.addTextNode("success", "success.update.folder");
        return resp;
    }

    /**
     * The getfoldertypes command retrieves a list of all object types.
     *
     * @param cmd HTTP request parameter map:
     *            <ul>
     *            <li>command=getfoldertypes</li>
     *            <li>ticket=session ticket</li>
     *            </ul>
     * @return XML-Response: List of FolderTypes
     *         <pre>
     *                          {@code
     *                          <folderTypes>
     *                              <folderType><id>1</id>...</folderType>
     *                          ...
     *                          </folderTypes>
     *                         }
     *                         </pre>
     * @see server.FolderType
     */
    @CinnamonMethod(checkTrigger = "true")
    public Response getFolderTypes(Map<String, String> cmd) {
        FolderTypeDAO ftDao = daoFactory.getFolderTypeDAO(em);
        List<FolderType> folderTypes = ftDao.list();
        XmlResponse resp = new XmlResponse(res);
        Element root = resp.getDoc().addElement("folderTypes");
        for (FolderType ft : folderTypes) {
            root.add(FolderType.asElement("folderType", ft));
        }
        return resp;
    }

    /**
     * The getuser command retrieves a user by the given id.
     *
     * @param cmd HTTP request parameter map:
     *            <ul>
     *            <li>command=getuser</li>
     *            <li>id=user id</li>
     *            <li>ticket=session ticket</li>
     *            </ul>
     * @return XML-Response: serialized user or error message.
     *         <pre>
     *                          {@code
     *                          <users>
     *                              <user><id>123</id>
     *                              ...
     *                              </user>
     *                          </users>
     *                         }
     *                         </pre>
     * @see server.User
     */
    @CinnamonMethod(checkTrigger = "true")
    public Response getUser(Map<String, String> cmd) {
        XmlResponse resp = new XmlResponse(res);

        Long id = ParamParser.parseLong(cmd.get("id"), "error.param.id");
        log.debug("getuser(" + id + ")");

        UserDAO userDAO = daoFactory.getUserDAO(em);
        Document doc = resp.getDoc();
        Element root = doc.addElement("users");
        User user = userDAO.get(id);
        root.add(User.asElement("user", user));
        return resp;
    }

    /**
     * The getuserbyname command retrieves a user by the given name.
     *
     * @param cmd HTTP request parameter map:
     *            <ul>
     *            <li>command=getuserbyname</li>
     *            <li>name=user login name</li>
     *            <li>ticket=session ticket</li>
     *            </ul>
     * @return XML-Response: serialized user or error message.
     * @see server.User
     * @see server.CmdInterpreter#getUser(java.util.Map)
     */
    @CinnamonMethod(checkTrigger = "true")
    public Response getUserByName(Map<String, String> cmd) {
        XmlResponse resp = new XmlResponse(res);

        UserDAO userDAO = daoFactory.getUserDAO(em);
        Document doc = resp.getDoc();
        Element root = doc.addElement("users");
        String name = cmd.get("name");
        User user = userDAO.findByName(name);
        root.add(User.asElement("user", user));
        return resp;
    }

    /**
     * The getusers command retrieves all users (currently without restricting them
     * to the group of the user issuing the query).
     *
     * @param cmd HTTP request parameter map:
     *            <ul>
     *            <li>command=getusers</li>
     *            <li>ticket=session ticket</li>
     *            </ul>
     * @return XML-Response: List of XML serialized users.
     *         <pre>
     *                          {@code
     *                          <users>
     *                              <user><id>444</id>...</user>
     *                              <user><id>555</id>...</user>
     *                              ...
     *                          </users>
     *                         }
     *                         </pre>
     */
    @CinnamonMethod(checkTrigger = "true")
    public Response getUsers(Map<String, String> cmd) {
        XmlResponse resp = new XmlResponse(res);

        UserDAO userDAO = daoFactory.getUserDAO(em);

        Document doc = resp.getDoc();
        List<User> users = userDAO.list();
        Element root = doc.addElement("users");
        for (User user : users) {
            root.add(User.asElement("user", user));
        }
        return resp;
    }


    /**
     * The getobject command retrieves an object by the given id.
     * <h2>Needed permissions</h2>
     * BROWSE_OBJECT
     *
     * @param cmd HTTP request parameter map:
     *            <ul>
     *            <li>command=getobject</li>
     *            <li>id=object id</li>
     *            <li>ticket=session ticket</li>
     *            </ul>
     * @return XML-Response:
     *         XML serialized object or xml-error-doc
     * @see server.data.ObjectSystemData
     */
    @CinnamonMethod(checkTrigger = "true")
    public Response getObject(Map<String, String> cmd) {
        Long id = ParamParser.parseLong(cmd.get("id"), "error.param.id");
        log.debug("getobject(" + id + ")");

        ObjectSystemDataDAO osdDAO = daoFactory.getObjectSystemDataDAO(em);

        XmlResponse resp = new XmlResponse(res);
        Document doc = resp.getDoc();
        Element root = doc.addElement("objects");
        ObjectSystemData osd = osdDAO.get(id);
        if (osd == null) {
            throw new CinnamonException("error.object.not.found");
        } else {
            (new Validator(user)).checkBrowsePermission(osd);
            root.add(osd.toXML().getRootElement());
        }
        return resp;
    }

    /**
     * The getObjectsById command retrieves one or more objects by the given id.
     * <p/>
     * <h2>Needed permissions</h2>
     * BROWSE_OBJECT
     *
     * @param cmd HTTP request parameter map:
     *            <ul>
     *            <li>command=getobjectsbyid</li>
     *            <li>ids= list of object ids in xml: //ids/id</li>
     *            <li>ticket=session ticket</li>
     *            </ul>
     * @return XML-Response:
     *         List of XML serialized objects.
     * @see server.data.ObjectSystemData
     */
    @SuppressWarnings("unchecked")
    @CinnamonMethod(checkTrigger = "true")
    public Response getObjectsById(Map<String, String> cmd) {
        XmlResponse resp = new XmlResponse(res);
        Document response = resp.getDoc();
        Element root = response.addElement("objects");

        Node rootParamNode = ParamParser.parseXml(cmd.get("ids"), "error.param.ids.xml");
        List<Node> idNodes = rootParamNode.selectNodes("id");
        ObjectSystemDataDAO osdDAO = daoFactory.getObjectSystemDataDAO(em);
        Validator validator = new Validator(user);
        Permission browsePermission = Permission.fetch(PermissionName.BROWSE_OBJECT);
        for (Node n : idNodes) {
            try {
                Long id = Long.parseLong(n.getText());
                ObjectSystemData osd = osdDAO.get(id);
                validator.checkBrowsePermission(osd, browsePermission);
                root.add(osd.toXML().getRootElement());
            } catch (Exception e) {
                log.debug("failed to add OSD for '" + n.getText() + "': " + e.getMessage());
            }
        }
        return resp;
    }

    /**
     * The getFoldersById command retrieves one or more folders by the given id.
     * <h2>Needed permissions</h2>
     * BROWSE_FOLDER
     *
     * @param cmd HTTP request parameter map:
     *            <ul>
     *            <li>command=getfoldersbyid</li>
     *            <li>ids= list of folder ids in xml: //ids/id</li>
     *            <li>ticket=session ticket</li>
     *            </ul>
     * @return XML-Response: List of XML serialized folders.
     *         <pre>
     *                          {@code
     *                          <folders>
     *                              <folder>...</folder>
     *                              ...
     *                          </folder>
     *                         }
     *                         </pre>
     * @see server.Folder
     */
    @SuppressWarnings("unchecked")
    @CinnamonMethod(checkTrigger = "true")
    public Response getFoldersById(Map<String, String> cmd) {
        XmlResponse resp = new XmlResponse(res);
        Document response = resp.getDoc();
        Element root = response.addElement("folders");

        Node rootParamNode = ParamParser.parseXml(cmd.get("ids"), "error.param.ids.xml");
        List<Node> idNodes = rootParamNode.selectNodes("id");
        FolderDAO fDao = daoFactory.getFolderDAO(em);
        Validator validator = new Validator(user);
        Permission browsePermission = Permission.fetch(PermissionName.BROWSE_FOLDER);
        for (Node n : idNodes) {
            try {
                Long id = Long.parseLong(n.getText());
                Folder folder = fDao.get(id);
                validator.validateFolderAgainstAcl(folder, browsePermission);
                folder.toXmlElement(root);
            } catch (Exception e) {
                log.debug("failed to add Folder for '" + n.getText() + "': " + e.getMessage());
            }
        }
        return resp;
    }


    /**
     * The listindexgroups command returns a list of all IndexGroups
     *
     * @param cmd HTTP request parameter map:
     *            <ul>
     *            <li>command=listindexgroups</li>
     *            <li>ticket=session ticket</li>
     *            </ul>
     * @return XML-Response:
     *         List of XML serialized IndexGroups (without IndexItems).
     * @see CmdInterpreter#getIndexGroup(Map)
     */
    @CinnamonMethod(checkTrigger = "true")
    public Response listIndexGroups(Map<String, String> cmd) {
        XmlResponse resp = new XmlResponse(res);
        Document response = resp.getDoc();
        Element root = response.addElement("indexGroups");

        IndexGroupDAO igDao = daoFactory.getIndexGroupDAO(em);
        List<IndexGroup> groups = igDao.list();
        for (IndexGroup group : groups) {
            group.toXmlElement(root);
        }
        return resp;
    }

    /**
     * The listindexgroups command returns a specific IndexGroup
     *
     * @param cmd HTTP request parameter map:
     *            <ul>
     *            <li>command=getindexgroup</li>
     *            <li>id=id of the group</li>
     *            <li>ticket=session ticket</li>
     *            </ul>
     * @return XML-Response: Serialized IndexGroup (with IndexItems).
     *         <pre>
     *                          {@code
     *                          <indexGroups>
     *                              <indexGroup>...</indexGroup>
     *                          ...
     *                         </indexGroups>
     *                         }
     *                         </pre>
     * @see server.index.IndexGroup
     */
    @CinnamonMethod(checkTrigger = "true")
    public Response getIndexGroup(Map<String, String> cmd) {
        XmlResponse resp = new XmlResponse(res);
        Document response = resp.getDoc();
        Element root = response.addElement("indexGroups");

        IndexGroupDAO igDao = daoFactory.getIndexGroupDAO(em);
        Long id = ParamParser.parseLong(cmd.get("id"), "error.param.id");
        IndexGroup group = igDao.get(id);
        group.toXmlElement(root, true);
        return resp;
    }

    /**
     * The listindexitems command returns a list of all IndexItems.
     *
     * @param cmd HTTP request parameter map:
     *            <ul>
     *            <li>command=listindexitems</li>
     *            <li>ticket=session ticket</li>
     *            </ul>
     * @return XML-Response:
     *         List of XML serialized IndexItems.
     *         <pre>
     *                          {@code
     *                          <indexItems>
     *                              <indexItem>...</indexItem>
     *                          </indexItems>
     *                         }
     *                         </pre>
     * @see server.index.IndexItem
     */
    @CinnamonMethod(checkTrigger = "true")
    public Response listIndexItems(Map<String, String> cmd) {
        XmlResponse resp = new XmlResponse(res);
        Document response = resp.getDoc();
        Element root = response.addElement("indexItems");

        IndexItemDAO iiDao = daoFactory.getIndexItemDAO(em);
        List<IndexItem> items = iiDao.list();
        for (IndexItem item : items) {
            item.toXmlElement(root);
        }
        return resp;
    }

    /**
     * The reindex command updates the index on all objects and folders.
     * Note: this is currently not optimized for performance - it may take a long time.
     * @param cmd HTTP request parameter map:
     *            <ul>
     *            <li>command=reindex</li>
     *            <li>ticket=session ticket</li>
     *            </ul>
     * @return XML-Response: number of affected items.
     *         <pre>
     *          {@code
     *           <success>success.reindex</success>
     *          }
     *         </pre>
     */
    @CinnamonMethod(checkTrigger = "true")
    public Response reindex(Map<String, String> cmd) {
        if (!user.verifySuperuserStatus(em)) {
            throw new CinnamonException("error.must_be_admin");
        }
        String target = cmd.get("target");
        FolderDAO folderDAO = daoFactory.getFolderDAO(em);
        folderDAO.prepareReIndex();

        XmlResponse resp = new XmlResponse(res);
        Document response = resp.getDoc();
        response.getRootElement().addElement("success").addText("success.reindex");
        return resp;
    }

    /**
     * The clearindex command removes all objects of a given class
     * from the Lucene index. Useful if you have removed items without
     * calling removeFromIndex for some reason (especially during testing).
     *
     * @param cmd HTTP request parameter map:
     *            <ul>
     *            <li>command=clearindex</li>
     *            <li>classname=Java class name</li>
     *            <li>ticket=session ticket</li>
     *            </ul>
     * @return XML status message.
     */
    @SuppressWarnings("unchecked")
    @CinnamonMethod(checkTrigger = "true")
    public Response clearIndex(Map<String, String> cmd) {
        if (!user.verifySuperuserStatus(em)) {
            throw new CinnamonException("error.must_be_admin");
        }
        String classname = cmd.get("classname");
        if (classname == null) {
            throw new CinnamonException("error.param.classname");
        }
        LuceneBridge lucene = repository.getLuceneBridge();
        try {
            Class clazz = Class.forName(classname);
            lucene.removeClassFromIndex(clazz);
        } catch (Exception e) {
            throw new CinnamonException("error.clearing.index", e);
        }

        XmlResponse resp = new XmlResponse(res);
        Document response = resp.getDoc();
        response.addElement("clearIndexResult").addText("clearindex.success");
        return resp;
    }

    /**
     * Search for items indexed by Lucene. This search will return <strong>all</strong> objects
     * and / or folders found.
     * <h2>Needed permissions</h2>
     * BROWSE_OBJECT oder BROWSE_FOLDER (per object)
     * <p>Note: search() is not exactly deprecated, but you should use searchObjects
     * or searchFolders, unless you really need search results which
     * include both Folders and OSDs.</p>
     *
     * @param cmd HTTP request parameter map:
     *            <ul>
     *            <li>command=search</li>
     *            <li>query= xml string with the Lucene-xml-query</li>
     *            <li>ticket=session ticket</li>
     *            </ul>
     * @return XML document with ids of objects and folders found.
     */
    @CinnamonMethod(checkTrigger = "true")
    public Response search(Map<String, String> cmd) {
        ResultCollector results;
        LuceneBridge lucene = repository.getLuceneBridge();
        results = lucene.search(cmd.get("query"));
        log.debug("Received search results, now filtering");
        if (user.verifySuperuserStatus(em)) {
            log.debug("No need to filter results for superuser.");
        } else {
            Validator val = new Validator(user);
            results.filterResults(val, null);
        }
        return new XmlResponse(res, results.getSearchResultsAsXML());
    }

    /**
     * Search for items indexed by Lucene. Searches over all fields, returns only the top results.
     * This is intended for searches made by clients with a simple one-text field search interface.
     * <h2>Needed permissions</h2>
     * BROWSE_OBJECT oder BROWSE_FOLDER (per object)
     *
     * @param cmd HTTP request parameter map:
     *            <ul>
     *            <li>command=search</li>
     *            <li>query= text query string</li>
     *            <li>page = the page number for paged results, starting with 0 for the first page</li>
     *            <li>page_size= maximum number of results to be returned</li>
     *            <li>ticket=session ticket</li>
     *            </ul>
     * @return XML document with ids of objects and folders found.
     */
    @CinnamonMethod(checkTrigger = "true")
    public Response searchSimple(Map<String, String> cmd) {
        ScoreDoc[] results;
        Integer page = ParamParser.parseInt(cmd.get("page"), "error.param.page");
        Integer maxResults = ParamParser.parseInt(cmd.get("page_size"), "error.param.max_results");
        final String[] fieldsForSimpleSearch = {"content",
                Constants.FIELD_FOLDER_PATH,
                Constants.FIELD_NAME,
                Constants.FIELD_OBJECT_TYPE_NAME
        };
        LuceneBridge lucene = repository.getLuceneBridge();
        SearchResult searchResult = lucene.searchMultipleFields(cmd.get("query"), page, maxResults, fieldsForSimpleSearch);
        log.debug("Received search results, now filtering");
        Validator val = new Validator(user);

        searchResult.filterDocuments(val);
        return new XmlResponse(res, searchResult.getSearchResultsAsXML());
    }

    /**
     * Sudo: create a new session for another user and receive his session ticket.<br>
     * This method can be used by external processes to work in the name of normal users. For example,
     * a user creates a task for the RenderServer. The render process now creates a rendition of an
     * object and wants to store it in the repository - but we need to check that a user cannot create
     * tasks that produce output in a form or place that he may not use. <br>
     * Note that the sudo API method can be dangerous, so it needs to be restricted. This is achieved by
     * having two fields in the User object:
     * <ul>
     * <li>sudoer = boolean value, true if the User is allowed to use the sudo command</li>
     * <li>sudoable = boolean value, true if the user may be the target of a sudo.</li>
     * </ul>
     * <p/>
     * Unless the system administrator needs to debug a specific task, administrator accounts should
     * always have the sudoable field set to false.
     *
     * @param cmd HTTP request parameter map:
     *            <ul>
     *            <li>command=sudo</li>
     *            <li>user_id= id of the user who you want to impersonate</li>
     *            <li>ticket=session ticket</li>
     *            </ul>
     * @return XML document with the ticket of the user's session.
     */
    @CinnamonMethod(checkTrigger = "true")
    public Response sudo(Map<String, String> cmd) {
        // check user_id parameter
        if (!cmd.containsKey("user_id")) {
            throw new RuntimeException("error.missing.param.user_id");
        }

        // check if current user may do sudo
        if (user.isSudoer()) {
            log.debug("User " + user.getName() + " is sudoer - ok.");
        } else {
            log.debug("User " + user.getName() + " may not do sudo.");
            throw new CinnamonException("error.sudo.forbidden");
        }

        // load user to which sudo is being done ;)
        UserDAO uDao = daoFactory.getUserDAO(em);
        User alias = uDao.get(cmd.get("user_id"));
        if (alias == null) {
            throw new CinnamonException("error.user.not_found");
        }

        // check if target is sudoable
        if (!alias.isSudoable()) {
            log.warn("User " + user.getName() + " tried to become " + alias.getName() + " via sudo, who is not a valid target.");
            throw new CinnamonException("error.sudo.misuse");
        }

        // create session for target user:
        Session session = new Session(repository.getName(), alias, "--- sudo by " + user.getName() + " ---", user.getLanguage());
        SessionDAO sessionDAO = daoFactory.getSessionDAO(em);
        sessionDAO.makePersistent(session);
        log.debug("session.ticket:" + session.getTicket());

        // create response:
        XmlResponse resp = new XmlResponse(res);
        Element sudoTicket = resp.getDoc().addElement("sudoTicket");
        sudoTicket.addText(session.getTicket());
        return resp;
    }

    /**
     * Search for OSDs indexed by Lucene.
     * <h2>Needed permissions</h2>
     * BROWSE_OBJECT (per object, otherwise it will be filtered)
     *
     * @param cmd HTTP request parameter map:
     *            <ul>
     *            <li>command=searchobjects</li>
     *            <li>query= xml string with the Lucene-xml-query</li>
     *            <li>ticket=session ticket</li>
     *            <li>[page_size] = optional, an integer value to use paged results</li>
     *            <li>[page] = optional, select which page of paged results to return.
     *            Defaults to 1 if [page_size] is used without
     *            a valid [page] value. Parameter is ignored if no page_size is set.</li>
     *            </ul>
     * @return XML-Response:
     *         <pre>
     *                          {@code
     *                          <objects>
     *                              <object><id>5</id>...</object>
     *                              <object>...</object>
     *                              <parentFolders>
     *                                  <folder>...</folder>
     *                              </parentFolders>
     *                          </objects>
     *                         }
     *                         </pre>
     */
    @CinnamonMethod(checkTrigger = "true")
    public Response searchObjects(Map<String, String> cmd) {
        Set<XmlConvertable> resultStore;
        resultStore = fetchSearchResults(cmd, ObjectSystemData.class);
        XmlResponse resp = new XmlResponse(res);
        Element root = resp.getDoc().addElement("objects");
        root.addAttribute("total-results", String.valueOf(resultStore.size()));

        if (cmd.containsKey("page_size")) {
            addPagedResultsToElement(root, resultStore, cmd);
        } else {
            for (XmlConvertable conv : resultStore) {
                conv.toXmlElement(root);
            }
        }

        addPathFolders(resp.getDoc());
        return resp;
    }

    void addPagedResultsToElement(Element root, Set<XmlConvertable> resultStore, Map<String, String> cmd) {
        List<XmlConvertable> itemList = new ArrayList<XmlConvertable>();
        itemList.addAll(resultStore);

        if (itemList.isEmpty()) {
            // if result list is empty, we can skip further sorting and serializing.
            return;
        }

        Collections.sort(itemList); // sort by id

        int pageSize = ParamParser.parseLong(cmd.get("page_size"), "error.param.max_results").intValue();
        int page = 1;
        if (cmd.containsKey("page")) {
            page = ParamParser.parseLong(cmd.get("page"), "error.param.page").intValue();
        }

        // to prevent index-out-of-bound exception:
        if (page <= 0) {
            page = 1;
        }
        if (pageSize <= 0) {
            pageSize = 1;
        }

        int start = pageSize * (page - 1);
        int end = pageSize * page;
        for (int x = start; x < end && x < itemList.size(); x++) {
            itemList.get(x).toXmlElement(root);
        }
    }

    /**
     * Search for items indexed by Lucene.
     * <h2>Needed permissions</h2>
     * BROWSE_FOLDER (per folder, otherwise it will be filtered)
     *
     * @param cmd HTTP request parameter map:
     *            <ul>
     *            <li>command=searchfolders</li>
     *            <li>query= xml string with the Lucene-xml-query</li>
     *            <li>ticket=session ticket</li>
     *            <li>[page_size] = optional, an integer value to use paged results</li>
     *            <li>[page] = optional, select which page of paged results to return.
     *            Defaults to 1 if [page_size] is used without
     *            a valid [page] value. Parameter is ignored if no page_size is set.</li>
     *            </ul>
     * @return XML-Response:
     *         <pre>
     *             {@code
     *              <folders>
     *                <folder><id>5</id>...</folder>
     *                <folder>...</folder>
     *                <parentFolders>
     *                  <folder>...</folder>
     *                </parentFolders>
     *              </folders>
     *             }
     *         </pre>
     */
    @CinnamonMethod(checkTrigger = "true")
    public Response searchFolders(Map<String, String> cmd) {
        Set<XmlConvertable> resultStore;
        resultStore = fetchSearchResults(cmd, Folder.class);
        XmlResponse resp = new XmlResponse(res);
        Element root = resp.getDoc().addElement("folders");
        root.addAttribute("total-results", String.valueOf(resultStore.size()));

        if (cmd.containsKey("page_size")) {
            addPagedResultsToElement(root, resultStore, cmd);
        } else {
            for (XmlConvertable conv : resultStore) {
                conv.toXmlElement(root);
            }
        }

        addPathFolders(resp.getDoc());

        // add parent folders of search results to enable display of folder structure without
        // repeated path reloads.


        return resp;
    }


    /**
     * Create a zip archive of a folder.
     *
     * @param cmd HTTP request map
     *            Required and [optional] request parameters are:
     *            <ul>
     *            <li>[latest_head]=(true, false) -
     *            if true, only include only the newest object in the main version branch (=head)</li>
     *            <li>[latest_branch]=(true, false) -
     *            if true, include only those objects that have the latestBranch flag set. This can be combined
     *            with the latest_head parameter to fetch the newest objects in head and branches.
     *            </li>
     *            <li>[target_folder_id]=optional: attach the zip file to an OSD and put it into this folder.</li>
     *            <li>[object_type_name]=optional: the name of the object type of the OSD to which the zip file is attached.</li>
     *            <li>[object_meta]=optional: metadata for the storage object.</li>
     *            </ul>
     * @return Cinnamon Response object
     */
    @CinnamonMethod(checkTrigger = "true")
    public Response zipFolder(Map<String, String> cmd) {
        FolderDAO fDao = daoFactory.getFolderDAO(em);
        Folder parentFolder = fDao.get(cmd.get("id"));
        if (parentFolder == null) {
            throw new CinnamonException("error.folder.not_found");
        }
        Boolean latestHead = null;
        if (cmd.containsKey("latest_head")) {
            latestHead = ParamParser.parseBoolean(cmd.get("latest_head"), "error.param.latest_head");
        }
        Boolean latestBranch = null;
        if (cmd.containsKey("latest_branch")) {
            latestBranch = ParamParser.parseBoolean(cmd.get("latest_branch"), "error.param.latest_branch");
        }

        Validator validator = new Validator(user);
        ZippedFolder zf = parentFolder.createZippedFolder(fDao, latestHead, latestBranch, validator);
        File zipFile = zf.getZipFile();

        if(cmd.containsKey("target_folder_id")){
            // create an OSD and store zip file.
            Folder targetFolder = fDao.get(cmd.get("target_folder_id"));
            if(targetFolder == null){
                throw new CinnamonException("error.folder.not.found");
            }
            validator.validateCreate(targetFolder);
            ObjectType objectType;
            ObjectTypeDAO otDao = daoFactory.getObjectTypeDAO(em);
            if(cmd.containsKey("object_type_name")){
                objectType = otDao.findByName(cmd.get("object_type_name"));
            }
            else{
                objectType = otDao.findByName(Constants.OBJTYPE_DEFAULT);
            }
            FormatDAO formatDAO = daoFactory.getFormatDAO(em);
            Format format = formatDAO.findByName("zip");
            if(format == null){
                throw new CinnamonException("error.missing.format", "zip");
            }
            final String filename = parentFolder.getName() + "_archive";
            ObjectSystemData osd = new ObjectSystemData(filename, user, targetFolder);
            osd.setType(objectType);
            if(cmd.containsKey("object_meta")){
                osd.setMetadata(cmd.get("object_meta"));
            }
            try{
                String contentPath = ContentStore.copyToContentStore(zipFile.getAbsolutePath(), repository.getName());
                if (contentPath.length() == 0) {
                    throw new CinnamonException("error.store.upload");
                }
                osd.setContentPathAndFormat(contentPath, format, repository.getName());
            }
            catch (IOException e){
                throw new CinnamonException("error.store.upload", e);
            }
            ObjectSystemDataDAO oDao = daoFactory.getObjectSystemDataDAO(em);
            oDao.makePersistent(osd);
            XmlResponse resp = new XmlResponse(res);
            resp.addTextNode("objectId", String.valueOf(osd.getId()));
            return resp;
        }
        else{
            return new FileResponse(res, zipFile.getAbsolutePath(), zipFile.length(), zipFile.getName());
        }
    }

    /**
     * Add a parentFolders node to the document's root node which contains
     * all referenced folder's ancestors up to the root node.
     *
     * @param doc document with serialized Folders and or OSDs.
     */
    @SuppressWarnings("unchecked")
    void addPathFolders(Document doc) {
        List<Node> parentFolders = doc.selectNodes("//folder/parentId|//object/parentId");
        log.debug("# of parentFolderNodes: " + parentFolders.size());
        FolderDAO fDao = daoFactory.getFolderDAO(em);
        /*
         * The second set "ids" is used so we do not have to perform full equals() on a potentially
         * large number of Folder objects.
         */
        Set<Long> ids = new HashSet<Long>();
        Set<XmlConvertable> folders = new HashSet<XmlConvertable>();
        for (Node node : parentFolders) {
            Long id = Long.parseLong(node.getText());
            if (ids.contains(id)) {
                // folder is already in result set.
                continue;
            }
            Folder folder = fDao.get(id);
            folders.add(folder);
            // check if we need to add the folder's parent:
            if (!ids.contains(folder.getParent().getId())) {
                List<Folder> parents = fDao.getParentFolders(folder);
                folders.addAll(parents);
                for (Folder parent : parents) {
                    ids.add(parent.getId());
                }
            }
        }

        // add serialized elements to response document:
        Element root = doc.getRootElement();
        Element pathFolderNode = root.addElement("parentFolders");
        for (XmlConvertable folder : folders) {
            folder.toXmlElement(pathFolderNode);
        }
    }

    Set<XmlConvertable> fetchSearchResults(Map<String, String> cmd, Class<? extends Indexable> indexable) {
        log.debug("start search");
        ResultCollector results = repository.getLuceneBridge().search(cmd.get("query"));
        log.debug("Received search results, now filtering");
        // log.debug(results.getSearchResultsAsXML().asXML());
        Validator val = new Validator(user);
        log.debug("before filterResults");
        return results.filterResults(val, indexable);
    }

    public Repository getRepository() {
        return repository;
    }


    public void setRepository(Repository repository) {
        this.repository = repository;
    }


    /**
     * @return the user
     */
    public User getUser() {
        return user;
    }

    /**
     * @param user the User to set
     */
    public void setUser(User user) {
        this.user = user;
    }

    public EntityManager getEm() {
        return em;
    }

    public void setEm(EntityManager em) {
        this.em = em;
    }

    @Override
    public void setRes(HttpServletResponse res) {
        this.res = res;
    }

    @Override
    public HttpServletResponse getRes() {
        return res;
    }


    protected void finalize() throws Throwable {
//        try {
//            log.debug("finalize CmdInterpreter.");
//        } finally {
//            super.finalize();
//        }
    }

    /**
     * The listMetasetTypes command retrieves a list of all metaset types.
     *
     * @param cmd HTTP request parameter map:
     *            <ul>
     *            <li>command=listmetasettypes</li>
     *            <li>ticket=session ticket</li>
     *            </ul>
     * @return XML-Response: List of MetasetTypes
     *         <pre>
     *{@code
     *      <metasetTypes>
     *          <metasetType>
     *              <id></id>
     *              ...
     *          </metasetType>
     *          ...
     *      </metasetTypes>
     * }
     *        </pre>
     * @see server.MetasetType
     */
    @CinnamonMethod(checkTrigger = "true")
    public Response listMetasetTypes(Map<String, String> cmd) {
        MetasetTypeDAO mtDao = daoFactory.getMetasetTypeDAO(em);
        List<MetasetType> metasetTypes = mtDao.list();
        XmlResponse resp = new XmlResponse(res);
        Element root = resp.getDoc().addElement("metasetTypes");
        for(MetasetType mt : metasetTypes){
            root.add(MetasetType.asElement("metasetType", mt));
        }
        return resp;
    }

    /**
     * The getMetaset command retrieves an object's metaset or a directly
     * fetches a metaset.
     * <h2>Needed permissions</h2>
     * READ_OBJECT_CUSTOM_METADATA
     *
     * @param cmd HTTP request parameter map:
     *            <ul>
     *            <li>command=getmetaset</li>
     *            <li>id=object id</li>
     *            <li>class_name=(Folder|OSD|Metaset)</li>
     *            <li>type_name=the name of the metaset type</li>
     *            <li>ticket=session ticket</li>
     *            </ul>
     * @return XML-Response:
     *         The metaset of the specified object or
     *         <pre>
     *             {@code
     *             <metaset id="0" type="$typeName" status="empty"/>
     *             }
     *         </pre>
     */
    @CinnamonMethod(checkTrigger = "true")
    public Response getMetaset(Map<String, String> cmd) {
        Long id = ParamParser.parseLong(cmd.get("id"), "error.param.id");
        String className = cmd.get("class_name");
        String typeName = cmd.get("type_name");
        Metaset metaset = null;
        if(className.equals("Folder")){
            // should use Java 7 with switch.
            FolderDAO fDao = daoFactory.getFolderDAO(em);
            Folder folder = fDao.get(id);
            if(folder == null){
                throw new CinnamonException("error.param.id");
            }
            (new Validator(user)).validateGetFolderMeta(folder);
            metaset = folder.fetchMetaset(typeName);
        }
        else if(className.equals("OSD")){
            ObjectSystemDataDAO oDao = daoFactory.getObjectSystemDataDAO(em);
            ObjectSystemData osd = oDao.get(id);
            if(osd == null){
                throw new CinnamonException("error.param.id");
            }
            (new Validator(user)).validateGetMeta(osd);
            metaset = osd.fetchMetaset(typeName);
        }
        else if(className.equals("Metaset")){
            MetasetDAO mDao = daoFactory.getMetasetDAO(em);
            metaset = mDao.get(id);
        }
        else{
            throw new CinnamonException("error.param.class_name");
        }

        Response resp;
        if(metaset == null){
            resp = new XmlResponse(res, "<metaset id='0' type='"+typeName+"' status='empty'/>");
        }
        else{
            resp = new TextResponse(res, metaset.getContent());
        }
        return resp;
    }

    /**
     * The setMetaset command creates sets / creates a metaset for an object.
     * <h2>Needed permissions</h2>
     * WRITE_OBJECT_CUSTOM_METADATA or EDIT_FOLDER
     *
     * @param cmd HTTP request parameter map:
     *            <ul>
     *            <li>command=setmetaset</li>
     *            <li>id=object id</li>
     *            <li>class_name=(Folder|OSD)</li>
     *            <li>type_name=the name of the metaset type</li>
     *            <li>content=XML string
     *            <pre>
     *              {@code
     *              <metaset type="$typeName">
     *              ... metaset data ...
     *              </metaset>
     *              }
     *            </pre>
     *            </li>
     *            <li>[write_policy]=optional, allowed values are write|ignore|branch, default is branch.
     *            On write, the content is written regardless of other items linking to this metaset.
     *            On ignore, the content is ignored if there are other references to the metaset.
     *            On branch, if other references exist, a separate metaset for this item is created.
     *            </li>
     *            <li>ticket=session ticket</li>
     *            </ul>
     * @return XML-Response:
     *         The metaset or an error message.
     */
    @CinnamonMethod(checkTrigger = "true")
    public Response setMetaset(Map<String, String> cmd) {
        Long id = ParamParser.parseLong(cmd.get("id"), "error.param.id");
        String className = cmd.get("class_name");
        IMetasetOwner metasetOwner = null;
        if(className.equals("Folder")){
            // should use Java 7 with switch.
            FolderDAO fDao = daoFactory.getFolderDAO(em);
            Folder folder = fDao.get(id);
            (new Validator(user)).validatePermission(folder.getAcl(), PermissionName.EDIT_FOLDER);
            metasetOwner = folder;
            folder.updateIndexOnCommit();
        }
        else if(className.equals("OSD")){
            ObjectSystemDataDAO oDao = daoFactory.getObjectSystemDataDAO(em);
            ObjectSystemData osd = oDao.get(id);
            (new Validator(user)).validateSetMeta(osd);
            metasetOwner = osd;
            osd.updateIndexOnCommit();
        }
        else{
            throw new CinnamonException("error.param.class_name");
        }
        MetasetTypeDAO mtDao = daoFactory.getMetasetTypeDAO(em);
        String typeName = cmd.get("type_name");
        MetasetType metasetType = mtDao.findByName(typeName);
        if(metasetType== null){
            throw new CinnamonException("error.param.type_name");
        }

        WritePolicy writePolicy = WritePolicy.BRANCH;
        if(cmd.containsKey("write_policy")){
            writePolicy = WritePolicy.valueOf(cmd.get("write_policy"));
        }

        MetasetService metasetService = new MetasetService();
        Metaset metaset = metasetService.createOrUpdateMetaset(metasetOwner, metasetType, cmd.get("content"), writePolicy);

        XmlResponse resp = new XmlResponse(res);
        resp.getDoc().add(Metaset.asElement("meta",metaset));
        return resp;
    }

    /**
     * The linkMetaset command links a metaset to an object.
     * <h2>Needed permissions</h2>
     * WRITE_OBJECT_CUSTOM_METADATA or EDIT_FOLDER
     *
     * @param cmd HTTP request parameter map:
     *            <ul>
     *            <li>command=linkmetaset</li>
     *            <li>id=object id</li>
     *            <li>class_name=(Folder|OSD)</li>
     *            <li>metaset_id=Id of the metaset</li>
     *            <li>ticket=session ticket</li>
     *            </ul>
     * @return XML-Response:
     *         <pre>
     *             {@code
     *             <success>success.link.metaset</success>
     *             }
     *         </pre>
     *         or a standard error message.
     */
    @CinnamonMethod(checkTrigger = "true")
    public Response linkMetaset(Map<String, String> cmd) {
        Long id = ParamParser.parseLong(cmd.get("id"), "error.param.id");
        String className = cmd.get("class_name");
        IMetasetOwner metasetOwner = null;
        if(className.equals("Folder")){
            // should use Java 7 with switch.
            FolderDAO fDao = daoFactory.getFolderDAO(em);
            Folder folder = fDao.get(id);
            (new Validator(user)).validatePermission(folder.getAcl(), PermissionName.EDIT_FOLDER);
            metasetOwner = folder;
            folder.updateIndexOnCommit();
        }
        else if(className.equals("OSD")){
            ObjectSystemDataDAO oDao = daoFactory.getObjectSystemDataDAO(em);
            ObjectSystemData osd = oDao.get(id);
            (new Validator(user)).validateSetMeta(osd);
            metasetOwner = osd;
            osd.updateIndexOnCommit();
        }
        else{
            throw new CinnamonException("error.param.class_name");
        }

        MetasetDAO mDao = daoFactory.getMetasetDAO(em);
        Metaset metaset = mDao.get(cmd.get("metaset_id"));
        if(metaset == null){
            throw new CinnamonException("error.param.metaset_id");
        }

        metasetOwner.addMetaset(metaset);
        return new XmlResponse(res, "<success>success.link.metaset</success>");
    }

    /**
     * The unlinkMetaset command removes the link between a metaset and an item.
     * <h2>Needed permissions</h2>
     * WRITE_OBJECT_CUSTOM_METADATA or EDIT_FOLDER
     *
     * @param cmd HTTP request parameter map:
     *            <ul>
     *            <li>command=unlinkmetaset</li>
     *            <li>id=object id</li>
     *            <li>class_name=(Folder|OSD)</li>
     *            <li>metaset_id=Id of the metaset</li>
     *            <li>ticket=session ticket</li>
     *            </ul>
     * @return XML-Response:
     *         <pre>
     *             {@code
     *             <success>success.unlink.metaset</success>
     *             }
     *         </pre>
     *         or a standard error message.
     */
    @CinnamonMethod(checkTrigger = "true")
    public Response unlinkMetaset(Map<String, String> cmd) {
        Long id = ParamParser.parseLong(cmd.get("id"), "error.param.id");
        String className = cmd.get("class_name");
        IMetasetOwner metasetOwner = null;
        if(className.equals("Folder")){
            // should use Java 7 with switch.
            FolderDAO fDao = daoFactory.getFolderDAO(em);
            Folder folder = fDao.get(id);
            (new Validator(user)).validatePermission(folder.getAcl(), PermissionName.EDIT_FOLDER);
            metasetOwner = folder;
            folder.updateIndexOnCommit();
        }
        else if(className.equals("OSD")){
            ObjectSystemDataDAO oDao = daoFactory.getObjectSystemDataDAO(em);
            ObjectSystemData osd = oDao.get(id);
            (new Validator(user)).validateSetMeta(osd);
            metasetOwner = osd;
            osd.updateIndexOnCommit();
        }
        else{
            throw new CinnamonException("error.param.class_name");
        }

        MetasetDAO mDao = daoFactory.getMetasetDAO(em);
        Metaset metaset = mDao.get(cmd.get("metaset_id"));
        if(metaset == null){
            throw new CinnamonException("error.param.metaset_id");
        }

        new MetasetService().unlinkMetaset(metasetOwner, metaset);
        return new XmlResponse(res, "<success>success.link.metaset</success>");
    }

    /**
     * The deleteMetaset command deletes a metaset, if allowed.
     * <h2>Needed permissions</h2>
     * WRITE_OBJECT_CUSTOM_METADATA or EDIT_FOLDER
     *
     * @param cmd HTTP request parameter map:
     *            <ul>
     *            <li>command=deletemetaset</li>
     *            <li>id=object id</li>
     *            <li>class_name=(Folder|OSD)</li>
     *            <li>type_name=type of the metaset</li>
     *            <li>[delete_policy]=optional, one of complete|allowed. This method will first try to delete
     *            all references to the metaset. Each connected item's ACL is checked, if the reference may
     *            be safely deleted. If delete_policy=complete, an exception is thrown if the process encounters
     *            a non-accessible object. With delete_policy=allowed, the process continues, trying to delete
     *            the rest.
     *            </li>
     *            <li>ticket=session ticket</li>
     *            </ul>
     * @return XML-Response:
     *         <pre>
     *             {@code
     *             <success>success.delete.metaset</success>
     *             }
     *         </pre>
     *         or a standard error message.
     */
    @CinnamonMethod(checkTrigger = "true")
    public Response deleteMetaset(Map<String, String> cmd) {
        Long id = ParamParser.parseLong(cmd.get("id"), "error.param.id");
        String className = cmd.get("class_name");
        IMetasetOwner metasetOwner = null;
        Validator validator = new Validator(user);
        if(className.equals("Folder")){
            // should use Java 7 with switch.
            FolderDAO fDao = daoFactory.getFolderDAO(em);
            Folder folder = fDao.get(id);
            metasetOwner = folder;
            folder.updateIndexOnCommit();
        }
        else if(className.equals("OSD")){
            ObjectSystemDataDAO oDao = daoFactory.getObjectSystemDataDAO(em);
            ObjectSystemData osd = oDao.get(id);
            metasetOwner =  osd;
            osd.updateIndexOnCommit();
        }
        else{
            throw new CinnamonException("error.param.class_name");
        }
   
        MetasetTypeDAO mtDao = daoFactory.getMetasetTypeDAO(em);
        MetasetType metasetType = mtDao.findByName(cmd.get("type_name"));
        if(metasetType== null){
            throw new CinnamonException("error.param.type_name");
        }

        DeletePolicy deletePolicy = DeletePolicy.valueOf(cmd.get("delete_policy").toUpperCase());

        MetasetService metasetService =  new MetasetService();        
        Collection<IMetasetOwner> affectedItems = metasetService.deleteMetaset(metasetOwner, metasetType, validator, deletePolicy);
        for(IMetasetOwner exOwner : affectedItems){
            LocalRepository.addIndexable(exOwner, IndexAction.UPDATE);
        }

        return new XmlResponse(res, "<success>success.link.metaset</success>");
    }

}
