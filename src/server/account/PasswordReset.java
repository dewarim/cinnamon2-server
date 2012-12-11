package server.account;

import java.io.StringWriter;
import java.util.Map;

import javax.persistence.EntityManager;
import javax.persistence.NoResultException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.velocity.Template;
import org.apache.velocity.VelocityContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import server.User;
import server.dao.DAOFactory;
import server.dao.UserDAO;
import server.exceptions.CinnamonException;
import server.interfaces.Repository;
import server.interfaces.Response;
import server.response.HtmlResponse;
import utils.HibernateSession;

public class PasswordReset {

	transient Logger log = LoggerFactory.getLogger(this.getClass());
	private Map<String, Repository> repositories;
	private Map<String,String[]> params;
	private HttpServletResponse resp;
	
	@SuppressWarnings("unchecked")
	public PasswordReset(HttpServletRequest request, HttpServletResponse resp, Map<String, Repository> repositories){
		this.repositories = repositories;
		this.resp = resp;
		params = request.getParameterMap();
	}
	
	/**
	 * <p>After the user has received a password reset mail, he is expected to
	 * click on the link to get to the password form. This method simply returns
	 * the form with the given parameters filled in.</p>
	 * <p>The link  has to call upon "$servletPath"/resetPasswordForm and must contain 
	 * the following parameters:</p>
	 * <ul>
	 * 	<li>repository =  the repository of this user account</li>
	 *  <li>login = the login name of the user (user.name)</li>
	 *  <li>uuid = a unique token (user.token) which was sent by mail to the user's email account</li>
	 * </ul>
	 * 
	 * @return an HTML response containing the form to set the password to a new value, or an error message.
	 */
	public Response resetPasswordForm(){
		/*
		 * 1. get Repository from "repository"
		 * 2. get username from "login"
		 * 3. get UUID token from "uuid"
		 * 4. compare token to user.token
		 * 5. return form with filled in parameters
		 */
		
		HtmlResponse response = new HtmlResponse(resp);
		TemplateProvider templateProvider = new TemplateProvider();
		StringWriter mailWriter = new StringWriter();
		DAOFactory daoFactory = DAOFactory.instance(DAOFactory.HIBERNATE);
		Repository repository = null;
		User user = null;
		try{
			String repositoryName = getSingleParam("repository");
			repository = repositories.get(repositoryName);
			if(repository == null){
				throw new CinnamonException("error.param.repository");
			}

			// lookup user
			EntityManager em = repository.getEntityManager();
			HibernateSession.setLocalEntityManager(em);
			UserDAO uDao = daoFactory.getUserDAO(em); 
			try{
				user = uDao.findByName(getSingleParam("login"));
			}
			catch (NoResultException e) {
				throw new CinnamonException("error.invalid.loginname");
			}

			// lookup token
			String token = getSingleParam("uuid");
			if(token == null || ! token.equals(user.getToken()) ){
				throw new CinnamonException("error.param.token");
			}

			// return form with parameters
			Template template = 
				templateProvider.fetchTemplate(user.getLanguage(), "reset_password_form.vt");
			VelocityContext context = templateProvider.addTemplateParams(template, user, repository.getName());
			template.merge(context, mailWriter);
		}
		catch (Exception e) {
			/*
			 * get error template
			 * add exception message
			 */
			mailWriter = templateProvider.createErrorPage(user, repository, e, "reset_password_error.vt");
		}
		response.setContent(mailWriter.toString());
		return response;
	}
	
	String getSingleParam(String name){
		String sp;
		try{
			sp = params.get(name)[0];
		}
		catch (Exception e) {
			sp = null;
		}
		return sp;
	}
	
}
