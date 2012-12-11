// cinnamon - the Open Enterprise CMS project
// Copyright (C) 2007 Dr.-Ing. Boris Horner
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

package server;

import java.io.IOException; 

import java.sql.SQLException;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.fileupload.FileItemFactory;
import org.apache.commons.fileupload.disk.DiskFileItemFactory;
import org.dom4j.Element;
import org.dom4j.Node;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import server.global.Constants;
import server.interfaces.Repository;
import server.interfaces.Response;
import server.response.XmlResponse;
import server.account.MailValidator;
import server.account.PasswordReset;
import server.global.Conf;
import server.global.ConfThreadLocal;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.joran.JoranConfigurator;

public class CmnServer extends HttpServlet
{
	private transient Logger log = LoggerFactory.getLogger(this.getClass());
	private static final long	serialVersionUID	= 1L;
	private Conf conf = ConfThreadLocal.getConf();

	private Map<String, Repository> repositories = new HashMap<String, Repository>();
	
	private FileItemFactory diskFileItemFactory;
	private HttpPostParser httpPostParser;
	
	public CmnServer(){
	}
	
	@SuppressWarnings("unchecked")
	@Override
	public void init()
		throws ServletException {
		try {
			// TODO: support configurable repositories
			log.info("Server configuration loaded: " + conf.getConfigPath());
			
			if(conf.getLogbackConfigurationPath() != null){
				LoggerContext context = (LoggerContext) LoggerFactory
						.getILoggerFactory();
				// context.putProperty("application-name", NAME_OF_CURRENT_APPLICATION);
				JoranConfigurator jc = new JoranConfigurator();
				jc.setContext(context);
				context.reset(); // override default configuration
				jc.doConfigure(conf.getLogbackConfigurationPath());
			}
			
			diskFileItemFactory = new DiskFileItemFactory();
			httpPostParser =new HttpPostParser();

			for (String repositoryName : conf.getRepositoryList()) {
				log.debug("Found repository in configuration: " + repositoryName);
				Repository repository;
				try {
					repository = new CinnamonRepository(repositoryName);
				} catch (Exception e) {
					log.debug("", e);
					throw new ServletException(e);
				}
				
				repositories.put(repository.getName(), repository);
			}
			
			if(conf.getField("cinnamon_config/startIndexServer", "false").equals("true") ){
				log.debug("IndexServer is enabled, starting one thread per repository.");
				// after all repositories are initialized, start their IndexServers.
				for(Repository repo : repositories.values()){
					repo.startIndexServer();					
				}
			}
			else{
				log.info("IndexServer is disabled. To start IndexServer, add startIndexServer (true) to config.");
			}
			if(conf.getField("cinnamon_config/startWorkflowServer", "false").equals("true") ){
				log.debug("WorkflowServer is enabled, starting one thread per repository.");
				// after all repositories are initialized, start their WorkflowServers.
				for(Repository repo : repositories.values()){
					repo.startWorkflowServer();					
				}
			}
			else{
				log.info("WorkflowServer is disabled. To start WorkflowServer, add startWorkflowServer (true) to config.");
			}
			
		} catch (ServletException e) {
			throw e;
		} catch (Exception e) {
			log("Exception (was DBException)", e);
			throw new ServletException(e);
		}
	}

	
    @SuppressWarnings("unchecked")
	@Override
	public void doPost(HttpServletRequest req, HttpServletResponse res)
	throws ServletException, IOException
    {
	    // Parse request and respond to client 
        
        try {
    		log.debug("Post A");
    		Map<String,Object> cmd  ;
    		try{    			
    			cmd= httpPostParser.parse(req,conf, diskFileItemFactory);
    		}
    		catch (Exception e) {
    			log.warn("Creating error response.");
    			XmlResponse response = new XmlResponse(res);
    			Element root = response.getDoc().addElement("error");
    			root.addElement("message").addText("Error encountered while parsing request:\n" +
    						e.getLocalizedMessage());
                root.addElement("code").addText("error.parsing.request");
    			response.write();
    			throw e;
			}

    		CmdInterpreter ci = new CmdInterpreter();
			ci.interpret(cmd,res,repositories);
//    		log.debug("Post C "+cmd.get("command"));
		} catch (Exception e) {	
    		log.debug("", e);
		}
		
    }

	@Override
	public void doGet( HttpServletRequest request, HttpServletResponse resp )
  		throws ServletException, IOException {

		String path = request.getRequestURI();
		log.debug("requestURI: "+path);
		/*
		 * Note: this approach does not scale. If we get any more GET requests,
		 * they will need their own method resolution like we have for POST.
		 * (We cannot simply take the existing CmdInterpreter, as this would allow clients
		 * to use GET with normal Cinnamon API methods, which we do not want.)
		 */
		Response response;
		if(path.contains("/validateMail")){
			// user tries to validate a mail
			response = new MailValidator(request, resp, repositories).validateMail();
		}
		else if(path.contains("/resetPasswordForm")){
			response = new PasswordReset(request, resp, repositories).resetPasswordForm();
		}
		else{
			// default response: list repositories
			XmlResponse r = new XmlResponse(resp);
			Element root = r.getDoc().addElement("repositories");
			for(Node repositoryNode : conf.getRepositories()){
				Element repo = root.addElement("repository");
				repo.addElement("name").addText(repositoryNode.selectSingleNode("name").getText());

                List<Node> categories = repositoryNode.selectNodes("categories/category");
                Element cats = repo.addElement("categories");
                for (Node category : categories){
                    cats.addElement("category").addText(category.getText());
                }                
			}
            root.addElement("version").addText(Constants.SERVER_VERSION);
			response = r;
		}
		response.write();
	}

	@Override
	public void destroy() {
	}
}
