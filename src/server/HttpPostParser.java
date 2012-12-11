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

package server;

import java.io.*;
import java.util.*;

import javax.servlet.http.HttpServletRequest;

import org.apache.commons.fileupload.*;
import org.apache.commons.fileupload.servlet.ServletFileUpload;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import server.data.ContentStore;
import server.data.UploadedFile;
import server.exceptions.CinnamonConfigurationException;
import server.global.Conf;

public class HttpPostParser {

	private transient Logger log = LoggerFactory.getLogger(this.getClass());

	@SuppressWarnings("unchecked")
	public Map parse(HttpServletRequest req, Conf conf, FileItemFactory factory)
		throws IOException {

		Map<String,Object> result=new HashMap<String,Object>(); // <S,O> = better than O,O.

		try{
			//			Create a new file upload handler
			ServletFileUpload upload = new ServletFileUpload(factory);

			//			Parse the request

//			log.debug("Parse G");
			List<FileItem> items = upload.parseRequest(req);

			//			Process the uploaded items
//			log.debug("Parse H");
			for (FileItem item : items) {
				//		    	Process a regular form field
				if (item.isFormField()) {
//					log.debug("Parse I field");
					String name = item.getFieldName();
					String value = item.getString();
//					log.debug( "Form Field: "+name+"='"+value+"'");
					result.put(name, value);
				}
				else{
//					log.debug("Parse I file");
					//	Process a file upload
					String fieldName = item.getFieldName();
					String fileName = item.getName();					
					String contentType = item.getContentType();
					long sizeInBytes = item.getSize();

					String internalFileName=UUID.randomUUID().toString();
					String fileBufferFolder = conf.getDataRoot() + "file-buffer";
					if(! new File(fileBufferFolder).exists()){
						throw new CinnamonConfigurationException("Folder 'file-buffer' in "+
								conf.getDataRoot() + " is missing.");
					}
					String internalFilePath= fileBufferFolder + 
						File.separator + internalFileName;

					File uploadedFile = new File(internalFilePath);
                    InputStream inStream = item.getInputStream();
                    ContentStore.copyStreamToFile(inStream, uploadedFile);
                    inStream.close();

					item.write(uploadedFile);
					UploadedFile uf=new UploadedFile(internalFilePath,
							fieldName,
							fileName,
							contentType,
							sizeInBytes);
					result.put("file", uf);
					
					log.debug("Upload: " + fieldName + "=" + fileName);
				}

			}
		}
		catch (Exception e) {
			log.debug("",e);
			FileWriter errFile = new FileWriter(conf.getSystemRoot() + "global" + 
					conf.getSep() + "log" + 
					conf.getSep() + "cinnamon-err.log", true);
			PrintWriter toErrFile = new PrintWriter(errFile);
			toErrFile.println(e.getMessage());
			toErrFile.close();
			throw new RuntimeException(e);
		}
		return result;
	}
	
}
