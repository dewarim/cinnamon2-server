package server.account;

import java.io.File;
import java.io.StringWriter;

import org.apache.velocity.Template;
import org.apache.velocity.VelocityContext;
import org.apache.velocity.app.Velocity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import server.User;
import server.exceptions.CinnamonException;
import server.global.ConfThreadLocal;
import server.i18n.Language;
import server.i18n.UiLanguage;
import server.interfaces.Repository;

public class TemplateProvider {
	
	transient Logger log = LoggerFactory.getLogger(this.getClass());
	String pathToTemplates;
	public TemplateProvider(){
		pathToTemplates = ConfThreadLocal.getConf().getSystemRoot()+File.separator+"templates";
	}
	
	public Template fetchTemplate(UiLanguage language, String filename){
		String isoCode = "und";
		if(language != null){
			/*
			 * Actually, we are cheating a little. If the repository is not found,
			 * we will get null as language. In that case, let's assume that there
			 * is an "und" folder. If the admin has not installed an 'und'-folder
			 * as stated in the docs, that's not our fault.
			 */
			isoCode = language.getIsoCode();
		}
		
		try{
			pathToTemplates = pathToTemplates + File.separator + isoCode;
			if (! new File(pathToTemplates).exists()){
				log.warn("Requested path "+pathToTemplates+" does not exist.");
				log.debug("trying default language");
				if( UiLanguage.getDefaultLanguage().equals(language)){
					throw new CinnamonException("Could not find the template folder for the default language.");
				}
			}
			Velocity.setProperty(Velocity.FILE_RESOURCE_LOADER_PATH, pathToTemplates);
            Velocity.setProperty(Velocity.RUNTIME_LOG_LOGSYSTEM, new Slf4LogChute());
			Velocity.init();
			System.out.println();
			log.debug(filename);
			return Velocity.getTemplate(filename);
		}
		catch (Exception e) {
			throw new CinnamonException("error.load.template", e);
		}
	}
	
	public VelocityContext addTemplateParams(Template template, User user, String repositoryName){
		VelocityContext context = new VelocityContext();
		String serverUrl = ConfThreadLocal.getConf().getField("cinnamon_config/server-url", "http://localhost/");
		context.put("serverUrl", serverUrl);
		context.put("user", user);
		context.put("repository", repositoryName);
		context.put("minimumPasswordLength", ConfThreadLocal.getConf().getField("cinnamon_config/minimumPasswordLength", "4"));
		return context;
	}
	
	public StringWriter createErrorPage(User user, Repository repository, Exception ex, String filename){
		StringWriter writer = new StringWriter();
		UiLanguage lang = null;
		if(repository != null){
			/*
			 *  Do not call upon getDefaultLanguage without a valid Repository.
			 *  That's a big boo-boo! (Because without a repository, we do not get a valid
			 *  EntityManager).
			 */
			lang = UiLanguage.getDefaultLanguage();
			if(user != null){
				lang = user.getLanguage();
			}
		}
		
		Template template = fetchTemplate(lang, filename);
		VelocityContext context = new VelocityContext();
		context.put("error", ex.getLocalizedMessage());						
		try{
			template.merge(context, writer);
		}
		catch (Exception ext) {
			writer.append("Error: ");
            writer.append(ext.getLocalizedMessage());
            writer.append("\n Source: ");
            writer.append(ex.getLocalizedMessage());
		}
		return writer;
	}
	
	public String getPathToTemplates(){
		return pathToTemplates;
	}
}
