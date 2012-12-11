package server.extension;

import javax.persistence.EntityManager;
import javax.servlet.http.HttpServletResponse;

import server.User;
import server.interfaces.ApiClass;
import server.interfaces.ApiProvider;
import server.interfaces.CommandRegistry;
import server.interfaces.Repository;

public abstract class BaseExtension extends ApiClass implements ApiProvider  {
	
	protected User user;
	protected EntityManager em;
	protected HttpServletResponse res;
	protected Repository repository;
	static Class<?> clazz = null;
	

	/**
	 * @return the user
	 */
	public User getUser() {
		return user;
	}
	/**
	 * @param user the user to set
	 */
	public void setUser(User user) {
		this.user = user;
	}
	/**
	 * @return the em
	 */
	public EntityManager getEm() {
		return em;
	}
	/**
	 * @param em the em to set
	 */
	public void setEm(EntityManager em) {
		this.em = em;
	}
	/**
	 * @return the res
	 */
	public HttpServletResponse getRes() {
		return res;
	}
	/**
	 * @param res the res to set
	 */
	public void setRes(HttpServletResponse res) {
		this.res = res;
	}
	/**
	 * @return the repository
	 */
	public Repository getRepository() {
		return repository;
	}
	/**
	 * @param repository the repository to set
	 */
	public void setRepository(Repository repository) {
		this.repository = repository;
	}
	
//	@Override	
//	public CommandRegistry findAndRegisterMethods(CommandRegistry cmdReg) {
//		Map<String, Class<?>> methods = findMethods(clazz);
//		return registerMethods(cmdReg, methods);
//	}

    public static void setExtensionClass(Class<? extends BaseExtension> extensionClass){
		clazz = extensionClass;
	}
}
