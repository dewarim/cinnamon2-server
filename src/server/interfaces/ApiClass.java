package server.interfaces;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

import javax.persistence.EntityManager;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import server.CinnamonMethod;
import server.CinnamonMethodContainer;
import server.User;
import server.exceptions.CinnamonConfigurationException;
import server.exceptions.CinnamonException;
import server.extension.BaseExtension;
import utils.HibernateSession;

/**
 * A class which provides an extension to the Cinnamon server's API.
 *
 */
public abstract class ApiClass {
	private Logger log = LoggerFactory.getLogger(this.getClass());

    public abstract CommandRegistry registerApi(CommandRegistry cmdReg);

    public CommandRegistry findAndRegisterMethods(CommandRegistry cmdReg, ApiClass extension) {
		Map<String, Class<?>> methods = findMethods(extension.getClass());
		return registerMethods(cmdReg, methods);
	}
	
	public Map<String, Class<?>> findMethods( Class<?> classList[]){
		Map<String, Class<?>> methods = new HashMap<String, Class<?>>(); 
		log.debug("initialize methods for "+ classList.length +" classes.");
		for(Class<?> clazz : classList){
			log.debug("Examine class: "+clazz.getName());
			for(Method method : clazz.getMethods()){
//				log.debug("method: "+method.getName());
				if(method.isAnnotationPresent(CinnamonMethod.class)){
					log.debug("found method "+method.getName());
					methods.put(method.getName(), clazz);
				}
			}
		}
		return methods;
	}
	
	public Map<String, Class<?>> findMethods( Class<?> clazz){
		Class<?> classArray[] = {clazz};
		return findMethods(classArray);
	}	
	
	@SuppressWarnings("unchecked")
	public CommandRegistry registerMethods(CommandRegistry cmdReg, Map<String, Class<?>> methods){
		Class[] parameterTypes = new Class[] {Map.class};
		Map<String, MethodContainer> api = new HashMap<String, MethodContainer>();
		
		try{
			for (String methodName : methods.keySet()){
				Class<?> clazz = methods.get(methodName);
				Method command = clazz.getDeclaredMethod(methodName, parameterTypes );

				Boolean trigger = false;
				CinnamonMethod annotation = command.getAnnotation(CinnamonMethod.class);
				if(annotation.checkTrigger().equals("true")){
					log.debug("detected checkTrigger on: "+methodName);
					trigger = true;
				}
				MethodContainer mc = new CinnamonMethodContainer(methodName, clazz , command, trigger);
				api.put(methodName.toLowerCase(), mc);
			}
			cmdReg.registerAPI(api);
		}
		catch(NoSuchMethodException e){
			e.printStackTrace();
			throw new CinnamonConfigurationException("Method not found!:\\n"+e.getMessage());
		}
		return cmdReg;
	}
	
	public void croakUnlessSuperuser(String message, User user){
		EntityManager em = HibernateSession.getLocalEntityManager();
		if ( ! user.verifySuperuserStatus(em) ) {
			if(message == null){
				throw new CinnamonException("error.must_be_admin");
			}
			else{
				throw new CinnamonException(message);
			}
		}
	}
}
