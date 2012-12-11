package server.account;

import java.io.IOException;
import java.io.StringWriter;
import java.util.Map;

import javax.persistence.EntityManager;
import javax.persistence.EntityTransaction;
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

public class MailValidator {

	transient Logger log = LoggerFactory.getLogger(this.getClass());
	private Map<String, Repository> repositories;
	private Map<String,String[]> params;
	private HttpServletResponse resp;
	
	@SuppressWarnings("unchecked")
	public MailValidator(HttpServletRequest request, HttpServletResponse resp, Map<String, Repository> repositories){
		this.repositories = repositories;
		this.resp = resp; 
		params = request.getParameterMap();
	}
	
	/**
	 * <p>The link has to call upon "$servletPath"/validateMail and must contain the following parameters:</p>
	 * <ul>
	 * 	<li>repository =  the repository of this user account</li>
	 *  <li>login = the login name of the user (user.name)</li>
	 *  <li>uuid = a unique token (user.token) which was sent by email to the new address</li>
	 *  <li>email = the new email address</li>
	 * </ul>
	 * 
	 * @return an HTML response containing the form to set the password to a new value, or an error message.
	 * @throws IOException if the Velocity template for the HTML output cannot be read
	 */
	public Response validateMail(){
	
		/*
		 * 1. get Repository from "repository"
		 * 2. get user from "login"
		 * 3. get email from "email"
		 * 4. get UUID token from "uuid"
		 * 5. compare token to user.token
		 * 5.1 send confirmation email to old account?
		 * 6. set email to new value.
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
				throw new CinnamonException("error.param.loginname");
			}

			// lookup email
			String email = getSingleParam("email");
			if(email == null){
				throw new CinnamonException("error.param.email");
			}

			// lookup token
			String token = getSingleParam("uuid");
			if(token == null || ! token.equals(user.getToken()) ){
				throw new CinnamonException("error.param.token");
			}

			EntityTransaction et = em.getTransaction();
			try{
				et.begin();
				User myUser = uDao.get(user.getId()); // get managed user object
				myUser.setEmail(email);
				myUser.clearToken();
				et.commit();
			}
			catch (Exception e) {
				log.error("error during email validation:\n", e);
				try{
					et.rollback();
				}
				catch (Exception ex) {
					log.error("error during database rollback:\n",ex);
				}
				throw new CinnamonException("error.set.email.failed");
			}
			finally{
				if(em.isOpen()){
					em.close();
				}
			}
			// create success response:
			Template template = 
				templateProvider.fetchTemplate(user.getLanguage(), "validate_email_success.vt");
			VelocityContext context = new VelocityContext();
			template.merge(context, mailWriter);
		}
		catch (CinnamonException e) {
			/*
			 * get error template
			 * add exception message
			 */
			mailWriter = templateProvider.createErrorPage(user, repository, e, "validate_email_fail.vt");
		}
		catch (Exception e) {
			/*
			 * This error is expected to occur when there is an IO-problem with the success template.
			 * In that case, it is quite probable that we will not be able to show anything more
			 * useful than an error message, so we do just that:
			 */
			log.error("validateMail failed.",e);
			throw new CinnamonException("error.validate.mail.fail",e);
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
