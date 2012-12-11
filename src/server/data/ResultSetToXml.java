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

package server.data;

import java.io.StringWriter;  
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.DOMImplementation;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import server.exceptions.CinnamonException;

public class ResultSetToXml {
	
	private Logger log = LoggerFactory.getLogger(this.getClass());
	
	private Document xml_;
	private Element root_;
	
	// TODO: change to dom4j and return the Document directly via XmlResponse.
	public ResultSetToXml(ResultSet rs) {
        try {
            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            DocumentBuilder db = dbf.newDocumentBuilder();
            DOMImplementation di = db.getDOMImplementation();

            xml_ = di.createDocument(null, "result", null);
            root_ = xml_.getDocumentElement();
            
			while (rs.next()) {
				Element row = (Element) root_.appendChild(xml_
						.createElement("row"));
				ResultSetMetaData rsmd = rs.getMetaData();
				for (int i = 1; i <= rsmd.getColumnCount(); ++i) {
					String colName = rsmd.getColumnName(i); // 1-based
					Element cell = (Element) row.appendChild(xml_
							.createElement(colName));
					cell
							.appendChild(xml_.createTextNode(rs
									.getString(colName)));
				}
			}
		}
        catch(Exception ex){
        	log.debug("An Exception occured while processing the resultset:\n"+ex.getMessage());
        	throw new CinnamonException("error.processing.result", ex);
        }
	}
		
	public String geStringRepresentation() throws TransformerException {
       DOMSource domSource = new DOMSource(xml_);
       StringWriter writer = new StringWriter();
       StreamResult result = new StreamResult(writer);
       TransformerFactory tf = TransformerFactory.newInstance();
       Transformer transformer = tf.newTransformer();
       transformer.transform(domSource, result);
       return writer.toString();
	}

}
