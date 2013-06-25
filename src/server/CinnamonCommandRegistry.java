package server;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import javax.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import server.dao.ChangeTriggerDAO;
import server.dao.DAOFactory;
import server.exceptions.CinnamonConfigurationException;
import server.exceptions.CinnamonException;
import server.helpers.PoBox;
import server.interfaces.ApiProvider;
import server.interfaces.CommandRegistry;
import server.interfaces.MethodContainer;
import server.interfaces.Repository;
import server.interfaces.Response;
import server.trigger.ChangeTrigger;
import server.trigger.ITrigger;
import utils.FileKeeper;
import utils.HibernateSession;

/**
 * Register new commands for the Cinnamon server.
 */
public class CinnamonCommandRegistry implements CommandRegistry {

    private transient Logger log = LoggerFactory.getLogger(this.getClass());
    static DAOFactory daoFactory = DAOFactory.instance(DAOFactory.HIBERNATE);
    private Map<String, MethodContainer> apiMap = new HashMap<String, MethodContainer>();
    private Boolean allowOverrides = false;

    public CinnamonCommandRegistry() {

    }

    public CinnamonCommandRegistry(Boolean allowOverrides) {
        this.allowOverrides = allowOverrides;
    }

    @Override
    public void registerAPI(Map<String, MethodContainer> commands) {
        for (Entry<String, MethodContainer> entry : commands.entrySet()) {
            if (apiMap.containsKey(entry.getKey())) {
                if (allowOverrides) {
                    apiMap.put(entry.getKey(), entry.getValue());
                }
                else {
                    log.error("Failed to register command '" + entry.getKey() +
                            "' - it already exists in CinnamonCommandRegistry.");
                    throw new CinnamonConfigurationException("Attempt to override existing api method");
                }
            }
            else {
                log.debug("registered: " + entry.getKey());
                apiMap.put(entry.getKey(), entry.getValue());
            }
        }
        for (String cmd : commands.keySet()) {
//			log.debug("register API command: "+cmd);
            if (apiMap.containsKey(cmd)) {


            }

        }
    }

    @SuppressWarnings("unchecked")
    @Override
    public Response invoke(String command, Map<String, Object> params, HttpServletResponse res,
                           User user, Repository repository) {
        log.debug("Start to invoke: " + command);
        //		log.debug("res: "+res);
        if (apiMap.containsKey(command)) {
            MethodContainer mc = apiMap.get(command);
            if (mc == null) {
                throw new CinnamonConfigurationException("apiMap does not contain command " + command);
            }
            try {
                Method method = mc.getMethod();
                Class cmdClass = mc.getMethodClass();
                ApiProvider provider = (ApiProvider) cmdClass.newInstance();
                provider.setEm(HibernateSession.getLocalEntityManager());
                provider.setRes(res);
                if (user != null) {
                    log.debug("set User on API-Provider class");
                    /*
                     *  ApiProvider.user is null before a user is logged in.
                     *  This should only happen for command=connect or
                     *  command=initializeDatabase.
                     */
                    provider.setUser(user);
                }
                else {
                    log.debug("User is null.");
                }

                if (repository == null) {
                    log.debug("Repository is null.");
                }
                provider.setRepository(repository);

                PoBox poBox = new PoBox(null, user, repository, params, command);

                if (mc.checkTrigger()) {
                    log.debug("executing pre-Triggers");
                    checkPreTriggers(poBox);
                }

                if(poBox.endProcessing){
                    if(poBox.response == null){
                        log.error("poBox says to end processing, but did not generate a valid response.");
                        throw new CinnamonException("error.pre.process.fail");
                    }
                }
                
                log.debug("found method. Next: method.invoke(...)");
                poBox.response = (Response) method.invoke(provider, params);

                if (mc.checkTrigger()) {
                    log.debug("executing post-Triggers");
                    checkPostTriggers(poBox);
                }
                if(poBox.endProcessing){
                    if(poBox.response == null){
                        log.error("poBox says to end processing, but did not generate a valid response.");
                        throw new CinnamonException("error.post.process.fail");
                    }
                }


                log.debug("output: " + poBox.response);
                return poBox.response;
            } catch (InvocationTargetException e) {
                log.debug("failed to invoke: ", e);
                throw new CinnamonException(findCause(e).getMessage());
            } catch (Exception e) {
                throw new CinnamonException("error.invoke.command", e, command, e.getMessage());
            }
        }
        else {
            throw new CinnamonConfigurationException("Unknown Command: '" + command + "'");
        }
    }

    Throwable findCause(Throwable e){
        Throwable cause = e.getCause();
        return cause == null ? e : findCause(cause);
    }

    PoBox checkPreTriggers(PoBox poBox) {
        String command = poBox.command;
        ChangeTriggerDAO ctDao = daoFactory.getChangeTriggerDAO(HibernateSession.getLocalEntityManager());
        log.debug("searching for all active pre-triggers for: " + command);
        List<ChangeTrigger> triggerList = ctDao.findAllByCommandAndPreAndActiveOrderByRanking(command);
        for (ChangeTrigger ct : triggerList) {
            log.debug("executing trigger: " + ct.getTriggerType().getName());
            Class<? extends ITrigger> triggerClass = ct.getTriggerType().getTriggerClass();
            ITrigger it = getNewTriggerInstance(triggerClass);
            poBox = it.executePreCommand(poBox, ct.getConfig());
            if(poBox.endProcessing){
                break;
            }
        }
        return poBox;
    }

    PoBox checkPostTriggers(PoBox poBox) {
        String command = poBox.command;
        ChangeTriggerDAO ctDao = daoFactory.getChangeTriggerDAO(HibernateSession.getLocalEntityManager());
        log.debug("searching for all active post-triggers for: " + command);
        List<ChangeTrigger> triggerList = ctDao.findAllByCommandAndPostAndActiveOrderByRanking(command);
        log.debug("found: " + triggerList.size() + " triggers");
        for (ChangeTrigger ct : triggerList) {
            log.debug("executing trigger: " + ct.getTriggerType().getName());
            Class<? extends ITrigger> triggerClass = ct.getTriggerType().getTriggerClass();
            ITrigger it = getNewTriggerInstance(triggerClass);
            poBox = it.executePostCommand(poBox, ct.getConfig());
            if(poBox.endProcessing){
                break;
            }
        }
        return poBox;
    }

    ITrigger getNewTriggerInstance(Class<? extends ITrigger> triggerClass) {
        ITrigger it;
        if(triggerClass == null){
            throw new CinnamonException("error.param.class.is.null");
        }
        try {
            it = triggerClass.newInstance();
            log.debug("Found trigger class.");
        } catch (InstantiationException e) {
            throw new CinnamonException("error.instantiating.class", e, triggerClass.getName());
        }
        catch (IllegalAccessException e) {
            throw new CinnamonException("error.accessing.class", e, triggerClass.getName());
        }
        return it;
    }
    
    public Response executeAfterWorkTriggers(String command, Map<String, Object> params,
                                     HttpServletResponse res, User user, Repository repository, Response response ){
        ChangeTriggerDAO ctDao = daoFactory.getChangeTriggerDAO(HibernateSession.getLocalEntityManager());
        log.debug("searching for all active after-work triggers for: " + command);
        List<ChangeTrigger> triggerList = ctDao.findAllByCommandAndActiveAndAfterWorkOrderByRanking(command);
        if(triggerList.isEmpty()){
            return response;
        }
        else{
            for (ChangeTrigger ct : triggerList) {
                log.debug("executing afterWork trigger: " + ct.getTriggerType().getName());
                Class<? extends ITrigger> triggerClass = ct.getTriggerType().getTriggerClass();
                ITrigger it = getNewTriggerInstance(triggerClass);
                PoBox poBox = new PoBox(response, user, repository, params, command);
                poBox = it.executePostCommand(poBox, ct.getConfig());
                if(poBox.endProcessing){
                    break;
                }
            }
            return response;
        }
    }
}
