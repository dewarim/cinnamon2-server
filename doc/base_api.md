# Cinnamon Server Base API

Version: 2013-01-30 #1

This document describes the fundamental aspects of the Cinnamon 2 server API.

## Client-Server communication

The client talks to the server via HTTP by using HTTP Post requests (multipart/form-data).
The server responds with XML replies or binary data, as requested.

When using the default application server, the server address is
is: http://localhost:8080/cinnamon/cinnamon

* http / https: If configured, the application server and client may use HTTPS.
* localhost: the name or IP of the server
* 8080: the port is dependent on the application server - for example, Tomcat usually listens
  on port 80, but it's also perfectly ok to use an Apache httpd as a proxy server on port 80,
  which sends requests internally onward to the application server.
* cinnamon (first part of path) is the name of the WAR file
* cinnamon (second) is the servlet's traditional post request end point

Upon opening the server URL in a browser (or performing a GET request with any other client),
the server responds with a list of configured repositories. Each repository is a separate database,
containing users, acls, objects, folders and so on.

	<repositories>
		<repository>
			<name>demo</name>
			<categories>
				<category>demo</category>
				<category>dita</category>
			</categories>
		</repository>
		<version>2.4.1</version>
	</repositories>

The client logs in to the server by posting the user's credentials to the connect method:
    
    Request:
    command=connect
    repository=repository name
    user=user name / login
    pwd=password
    machine=machine name
    language= optional: ISO code of the client's language.

    Response:
    <connection>
	<ticket>$ticket</ticket>
    </connection>

Upon successful login, the server responds with a session ticket (UUID+repository name), which
can be used to authenticate the user for any number of subsequent requests. Session tickets are
valid for the duration configured in the server's configuration file (cinnamon_config.xml),
and their time-to-live is reset with each request.

If the login was unsuccessful, an XML reply containing error message will be returned.

After login, subsequent commnads take the following form:

      Request:
      command=getusers (all commands are lowercased)
      ticket=$ticket

      Response:
      <users>
		  <user><id>444</id><name>admin</name>...</user>
		  <user><id>555</id><name>john</name>...</user>
      </users>
      
      Request:
      command=getobjects
      ticket=$ticket
      parentid=id of the parent folder
      versions= one of: all, branch, head, default: head
      
      Response:
      <objects>
		  <object><id>...</id>...</object>
      </objects>

Items are commonly wrapped in a root element of the same name,
so requesting objects will return the above structure, while retrieving
a list of folders will look like this:

    <folders>
		<folder>...</folder>
		<folder>...</folder>
	</folders>
	
	
## API methods and permissions

To access an API method, the user may need authorization. This is commonly gained via ACLs,
which can be configured via the administration tool.

The permissions listed refer to the name of the constants in the class server.Permission.

If the user is not permitted to perform an action, an XML error message is returned, with
the exception of commands that return objects and folders: if a list of documents or folders
is requested, the server will filter all those elements which are not browsable by the user.

Note that the following lists of command use camelCase for improved redability. The server
still expects the command parameter to be send in lowercase.

Server administrators are exempt from permission checking.

### Commands which requre no permissions

*    clearIndex
*    clearMessage
*    createRelation
*    connect
*    createWorkflow
*    deleteRelation
*    disconnect
*    executeXmlQuery ( to view objects, you need: BROWSE_OBJECT )
*    getAclEntry
*    getAcls
*    getExtension
*    getFolderTypes
*    getFormats
*    getGroupsOfUser
*    getIndexGroup
*    getObjectTypes
*    getPermission
*    getRelations
*    getRelationTypes
*    getUser
*    getUserByName
*    getUsers
*    getUsersAcls
*    getUsersPermissions
*    getWorkflowTemplateList
*    initializeDatabase ( can be called by anybody as long as there are no user accounts in the database)
*    listAclEntries
*    listAclMembers
*    listGroups
* listIndexGroups
*    listIndexItems
*    listLanguages
* listUiLanguages
*    listMessages
*    listPermissions
*    reindex
 
### Commands that require Superuser permissions

Some of these will only be available via the administration extensions which are
part of the regular server installation. 

*    AddGroupToAcl
*    AddPermissionToAclEntry
*    AddUserToGroup
*    createAcl
*    createFolderType
*    createFormat
*    createGroup
*    createObjectType
*    createPermission
*    createRelationType
*    createUser
*    deleteAcl
*    deleteFolderType
*    deleteFormat
*    deleteGroup
*    deleteObjectType
*    deletePermission
*    deleteRelationType
*    deleteUser
*    editAcl
*    removeGroupFromAcl
*    removePermissionFromAcl
*    removeUserFromGroup

 
### Commands that require permissions via ACL

    copy
    READ_OBJECT_CONTENT
    READ_OBJECT_CUSTOM_METADATA
    READ_OBJECT_SYS_METADATA
    CREATE_OBJECT
	
    create
    CREATE_OBJECT
	
    createFolder
    CREATE_FOLDER
	
    delete
    DELETE_OBJECT
	
	deleteAllVersions
	DELETE_OBJECT
	
    deleteFolder
    DELETE_FOLDER
	
    getContent
    READ_OBJECT_CONTENT
	
    getFolder
    BROWSE_FOLDER
	
    getFolderByPath
    BROWSE_FOLDER (for each individual folder in the path, else this folder will be filtered)
	
    getFolderMeta
    READ_OBJECT_CUSTOM_METADATA
	
    getMeta
    READ_OBJECT_CUSTOM_METADATA
	
    getObject
    BROWSE_OBJECT
	
    getObjects
    BROWSE_OBJECT
	
    getObjectsById
    BROWSE_OBJECT
	
    getObjectsWithCustomMetadata
    READ_OBJECT_CUSTOM_METADATA	
    BROWSE_OBJECT
	
    getSubfolders	
    BROWSE_FOLDER for each subfolder
	
    getSysMeta
    READ_OBJECT_SYS_META or BROWSE_FOLDER, depending on type

	lock
    LOCK
	
    queryCustomTable
    QUERY_CUSTOM_TABLE
	
    queryFolders
    BROWSE_FOLDER
	
    queryObjects
    BROWSE_OBJECT
	
    search
    BROWSE_OBJECT or BROWSE_FOLDER (tested for each item found)
	
    searchFolders
    BROWSE_FOLDER (tested for each folder found)
	
    searchObjects
    BROWSE_OBJECT (tested for each object found)
	
    setContent
    WRITE_OBJECT_CONTENT
	
    setMeta
    WRITE_OBJECT_CUSTOM_METADATA
	
    setSysMeta
    LOCK and one of (WRITE_OBJECT_SYS_METADATA or  EDIT_FOLDER)
    with parameter aclId: SET_ACL instead of WRITE_OBJECT_SYS_METADATA
    with parameter parent_id: MOVE instead of WRITE_OBJECT_SYS_METADATA

## API extensions

It is possible to extend the server's API with your own classes and create new API methods.
Some plugins are already part of the default installation and can be activated via adding them
to the cinnamon_config.xml:

	 <apiClasses>
           <!-- The only obligatory apiClass: -->
	       <apiClass>server.CmdInterpreter</apiClass>
	       <!-- extensions: -->
	       <apiClass>server.extension.Initializer</apiClass> <!-- needed for testing -->
           <apiClass>server.extension.Translation</apiClass> 
           <apiClass>server.extension.WorkflowApi</apiClass>
           <apiClass>server.extension.LinkApi</apiClass>
	    </apiClasses>
	</apiClasses>
	
