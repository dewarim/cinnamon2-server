package server;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.persistence.EntityManager;
import javax.persistence.Query;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import server.dao.CustomTableDAO;
import server.dao.DAOFactory;
import server.dao.SessionDAO;
import server.data.SqlCustomConn;
import server.exceptions.CinnamonConfigurationException;
import server.global.ConfThreadLocal;
import server.helpers.AutoInstaller;
import server.index.IndexServer;
import server.index.LuceneBridge;
import server.interfaces.ApiClass;
import server.interfaces.CommandRegistry;
import server.interfaces.Repository;
import utils.DefaultPersistenceSessionProvider;
import utils.HibernateSession;
import utils.PersistenceSessionProvider;
import workflow.WorkflowServer;

public class CinnamonRepository implements Repository{

	private transient Logger log = LoggerFactory.getLogger(this.getClass());
	static DAOFactory daoFactory = DAOFactory.instance(DAOFactory.HIBERNATE);
	
	CommandRegistry commandRegistry = new CinnamonCommandRegistry();
	String name;
	HibernateSession hibernateSession;
    HibernateSession customHibernateSession;
	Map<String, SqlCustomConn> sqlCustomConns;
	IndexServer indexServer;
	Thread indexServerThread;
	WorkflowServer workflowServer;
	Thread workflowServerThread;
	LuceneBridge lucene;
	
	public CinnamonRepository(){
		
	}
		
	public CinnamonRepository(String name){

        this.name = name;
        ConfThreadLocal conf = ConfThreadLocal.getConf();
		String persistence_unit = conf.getPersistenceUnit(name);
		String url = conf.getDatabaseConnectionURL(name);

        // create Hibernate EntityManagers:
		hibernateSession = new HibernateSession(conf,name, conf.getPersistenceUnit(name ));

        log.debug("Loading custom connections for repository " + name);
        PersistenceSessionProvider psp = new DefaultPersistenceSessionProvider(name, persistence_unit, url);
        Query q = HibernateSession.getRepositoryEntityManager(psp).createNamedQuery("selectAllCustomTables");

        sqlCustomConns = new HashMap<String, SqlCustomConn>();
        for(CustomTable table : (List<CustomTable>) q.getResultList()){
            SqlCustomConn cust_con = new SqlCustomConn(table.getConnstring(),
                    table.getJdbcDriver(),
                    table.getAcl());
            sqlCustomConns.put(table.getName(),cust_con);
        }

		EntityManager em = hibernateSession.getEntityManager();
		this.lucene = new LuceneBridge(name, em);
		
		// delete all old sessions:
		purgeSessionTable(em);
		
		this.indexServer = new IndexServer(lucene, em, this);
		this.indexServerThread = new Thread(indexServer);
		this.workflowServer = new WorkflowServer(this);
		this.workflowServerThread = new Thread(workflowServer);

        // configure API for repository
        loadApiClasses( conf.getApiClasses(name) );
        initializeCustomPersistenceUnit();
        String autoInitializerXpath =   String.format("cinnamon_config/repositories/repository[name='%s']/auto-initialize", name);
        String autoInitialize = conf.getField( autoInitializerXpath, "false");
        if(autoInitialize.equals("true")){
            new AutoInstaller().initializeBasicSystem(this);
        }
	}

    public void initializeCustomPersistenceUnit(){
        ConfThreadLocal conf = ConfThreadLocal.getConf();
        // configure custom-persistence-units, if necessary:
        String customPersistenceXpath =
                String.format("cinnamon_config/repositories/repository[name='%s']/custom-persistence-unit", name);
        String customDbXpath =
                String.format("cinnamon_config/repositories/repository[name='%s']/custom-db", name);
        String customUnit = conf.getField(customPersistenceXpath, "");
        String customDb = conf.getField(customDbXpath, name + "_c");
        if (customUnit.length() > 0) {
            HibernateSession customSession = new HibernateSession(conf, customDb, customUnit);
            setCustomHibernateSession(customSession);
            log.debug("created new custom HibernateSession " + customSession);
        }
        else {
            log.debug("repository has no customUnit.");
        }
    }

	void purgeSessionTable(EntityManager em){
	    SessionDAO sessionDAO = daoFactory.getSessionDAO(em);
	    sessionDAO.deleteAll();
	}
	
	public void reloadSqlCustomConnections(CustomTableDAO ctDAO){
		List<CustomTable> tables = ctDAO.list();
		for(CustomTable table : tables){
			SqlCustomConn cust_con = new SqlCustomConn(table.getConnstring(),
					table.getJdbcDriver(),
					table.getAcl());
			sqlCustomConns.put(table.getName(), cust_con);
			log.debug(String.format("found connection: '%s'",table.getName()));
		}
	}

	@SuppressWarnings("unchecked")
	@Override
	public void loadApiClasses(List<String> apiClasses) {
		for(String className : apiClasses){
			log.debug("loading apiClasses for: "+className + " for repository: "+name);
			try{
				Class apiClass = Class.forName(className);
				Class[] paramTypes = new Class[]{CommandRegistry.class};
				Method register = apiClass.getDeclaredMethod("registerApi", paramTypes);
				log.debug("registerAPI for class: "+className);
				ApiClass acInstance = (ApiClass) apiClass.newInstance();
				register.invoke(acInstance, commandRegistry);
			}
			catch (Exception e) {
				log.error("failed to load API-class", e);
				throw new CinnamonConfigurationException("failed to load API-class", e);
			}
		}
	}
	
	public void startIndexServer(){
		indexServerThread.start();
	}
	
	public void startWorkflowServer(){
		workflowServerThread.start();
	}
	
	/**
	 * @return the name
	 */
	public String getName() {
		return name;
	}

	/**
	 * @param name the name to set
	 */
	public void setName(String name) {
		this.name = name;
	}

	/**
	 * @return the hibernateSession
	 */
	public HibernateSession getHibernateSession() {
		return hibernateSession;
	}

	/**
	 * @param hibernateSession the hibernateSession to set
	 */
	public void setHibernateSession(HibernateSession hibernateSession) {
		this.hibernateSession = hibernateSession;
	}

	/**
	 * @return the sqlCustomConns
	 */
	public Map<String, SqlCustomConn> getSqlCustomConns() {
		return sqlCustomConns;
	}

	/**
	 * @param sqlCustomConns the sqlCustomConns to set
	 */
	public void setSqlCustomConns(Map<String, SqlCustomConn> sqlCustomConns) {
		this.sqlCustomConns = sqlCustomConns;
	}

	/**
	 * @return the commandRegistry
	 */
	public CommandRegistry getCommandRegistry() {
		return commandRegistry;
	}

	/**
	 * @param commandRegistry the commandRegistry to set
	 */
	public void setCommandRegistry(CommandRegistry commandRegistry) {
		this.commandRegistry = commandRegistry;
	}

	public LuceneBridge getLuceneBridge(){
		return lucene;
	}
	
	public EntityManager getEntityManager(){
		return hibernateSession.getEntityManager();
	}

    public HibernateSession getCustomHibernateSession() {
        return customHibernateSession;
    }

    public void setCustomHibernateSession(HibernateSession customHibernateSession) {
        this.customHibernateSession = customHibernateSession;
    }
}
