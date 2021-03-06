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

package server.exceptions;

public class InvalidParameterException extends Exception {

	/**
	 * 
	 */
	private static final long	serialVersionUID	= 1L;

	public InvalidParameterException() {
		super();
	}

	public InvalidParameterException(String arg0) {
		super(arg0);
	}

	public InvalidParameterException(String arg0, Throwable arg1) {
		super(arg0, arg1);
	}

	public InvalidParameterException(Throwable arg0) {
		super(arg0);
	}

}
