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

package server.helpers;

import java.util.*;
import javax.persistence.EntityManagerFactory;
import javax.servlet.*;

import org.slf4j.LoggerFactory;
import org.slf4j.Logger;

import server.global.Conf;
import utils.HibernateSession;


public class HibernateListener implements ServletContextListener {

	private Logger log = LoggerFactory.getLogger(this.getClass());
	public Conf conf;
	
	public HibernateListener(){
		try {
			conf = new Conf();
		} catch (Exception e) {
			log.error("Could not instantiate HibernateListener: "+e.getMessage());
			// a missing or unusable configuration is not a recoverable exception:
			throw new RuntimeException(e);
		}		
	}
	
    public void contextInitialized(ServletContextEvent event) {
    	for (String repository : conf.getRepositoryList()) {
    		@SuppressWarnings("unused")
			HibernateSession hs = new HibernateSession(conf, repository, conf.getPersistenceUnit(repository));
    	}
    
    }

    public void contextDestroyed(ServletContextEvent event) {    	
    	HashMap<String, EntityManagerFactory> emfSet = HibernateSession.getEmf_hash();
    	for (EntityManagerFactory emf : emfSet.values()) {
			if (emf.isOpen()) {
				emf.close();
			}
		}
    	
    }
}