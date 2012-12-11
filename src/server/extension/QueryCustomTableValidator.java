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

package server.extension;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import server.Acl;
import server.Permission;
import server.User;
import server.data.Validator;
import server.global.PermissionName;

public class QueryCustomTableValidator extends Validator {
	
	@SuppressWarnings("unused")
	private Logger log = LoggerFactory.getLogger(this.getClass());

	public QueryCustomTableValidator(User user) {	
		this.user = user;
	}
	
	public void validateQueryCustomTable(Acl acl) {
		// currently everybody may get objects, but validation 
		// per object can suppress those without browse permission
		Permission queryCustomTable = fetchPermission(PermissionName.QUERY_CUSTOM_TABLE);
		validatePermission(acl, queryCustomTable);	
	}
		
}
