# Cinnamon server changelog

## 2.5.0

* Refactored indexing. Previous versions of Cinnamon were vulnerable to index corruption under bad conditions. (For example, power loss in the split second after an object was indexed but before the changes were written to the database). Also the existing indexing solution had to update the indexed object itself after indexing was done, which could lead to race conditions between one process making changes and the IndexServer updating the database to record its successful index operation.
* New table index_jobs: you must create a new table 'index_jobs' in each repository. See the server/doc/migration folder for an example SQL script (migration-2.5.0.sql). After the update, you may remove the indexOk and indexed columns from the objects and folders tables.
* Fixed Metaset creation: Metasets always should have a valid content field.
* setLifecycleState API changed slightly (and now works): parameter 'parameter' is now called 'config'.

## 2.4.3

* Fixed: predecessor OSD is indexed correctly if its latestHead/lastestBranch flag changes.
* Changed Lucene indexing from synchronous to asynchronous to prevent possible discrepancies between database-state after rollback and committed index updates. You must make sure that the IndexServer is running and has not a too large sleep time between two index runs (see configuration documentation on http://cinnamon-cms.de).
* Workflow Server will now log errors during task transitions to a new log metaset. You need to add a new metaset type "log".

## 2.4.2

+ Refactored latestHead/latestBranch in Translation and ObjectTreeCopier
+ Fixed OSD content deletion problem (after object was deleted in the database,
  it was possible for old files to stick around in the file system).
+ Improved documentation.

## 2.4.1

+ Improved build.xml, refactored ant build properties into their own file.

### Updates for libraries

* Lucene 3.5.0 to 3.6.1
* Hibernate 3.6.8 to 3.6.10
* SLF4J 1.6.4 to 1.7.2
* Logback 1.0.0 to 1.0.9
* Commons-Compress 1.3 to 1.4.1 (security update)
* Commons-IO 2.1 to 2.4
* Postgres JDBC driver 9.1-902 to 9.2-1002
* Javamail 1.4.4 to 1.4.5
* Commons-Codec 1.5 to 1.7

## 2.4.0

+ Moved to git as the new VCS.
+ New: LinkApi - create references to objects and folders.
  Database changes: add a table 'links' to your repositories, see doc/migration-2.3.1.sql on how to do this with Postgres.

## 2.3.0

+ New: made all CinnamonMethods check for possible ChangeTriggers (to help with customization) 
+ Fixed: copy would not set latesthead/latestbranch to true, but copy the source's state.
+ Fixed: MetasetService would in one case instantiate a second (unnecessary) MetasetService.
+ Fixed: delete OSD no longer sets additional object to latestHead in cases where the deleted
         object was the last part of a branch. (This happened if deleted object was for example v1-1 in
         a version tree with v1, v2)
+ Changed behaviour: Folder.setMetadata and OSD.setMetadata will now unlink metasets which are found in the metadata
         parameter. Old behaviour was to keep metasets. If you want to set an individual metaset, use the setMetaset
         API call.
+ New: Set language on user object upon connect() only if necessary, and use "und" if language param is empty.
+ Fixed: copy of an OSD will not inherit "locked" status from original.                   
         
## 2.2.2

+ Added Lifeycle Audit logging via ChangeTrigger. See doc/migration-2.2.2.sql for necessary database changes.
    To enable 
+ Fix broken query which could cause version number corruption.

## 2.2.1

+ Fixed: problem in internal API of osd.createClone(): would not automatically set the owner of an object, so the 
       clone was incomplete.
+ Fixed: bug in setMetadata: after conversion to metaset, OSD & Folder would not remove legacy metadata. 
+ Fixed: Creating a copy of an object (via translation API) could result in wrong latestHead / latestBranch information.
+ Fixed: A bug in Metaset handling could cause objects to have multiple metasets of the same type.

## 2.2.0

+ TikaParser is now configurable with regard to the file formats it will _not_ index.

    Just add an (optional) ConfigEntry with Dandelion the admin tool:
    name: tika.blacklist
    config: <config>
              <blacklist>xml|dita|ditamap</blacklist>
            </config>

+ OSDs may also be configured with regard to which formats should be parsed by XML/XPath indexers.

    Add an (optional) ConfigEntry:
    name: xml.format.list
    config: <config>
              <format>xml|dita|ditamap|html|xhtml</format>
            </config>

+ First version of Metaset API - custom metadata is now parsed into metaset objects of specific types.

    To upgrade, you need to run the sql script provided in Server/doc/migration-2.2.sql, which creates the following tables:
    metasets
    metaset_types
    osd_metasets
    folder_metasets

## 2.1.2

+ Fixed bug in copy()-API method which could prevent OSDs with relations from being copied.
+ Improved checking for xpath nodes which evaluate to NULL in workflow transition parameters.
+ First version of TikaParser. To build from source, you will need to add some new dependencies (see build.example.xml)

### Entitylib changes

  + removed XhtmlToPdfTransformer and dependencies(itext, xhtmlrenderer).
    If you need this class, you should include it as a plugin / additional jar.

## 2.1.1

+ Fixed bug in searchObjects API method: when using paged results,
  accessing the last page could result in an exception message.
+ createZippedFolder by default now uses Cp437 as encoding for filenames in zip archive. #entitylib
+ contentLength of server responses is now calculated correctly for XML, text/plain and HTML responses which
  use non-ascii characters.
  
### Safran (Java clientlib) changes:

  + API changed: getUsers() now returns String instead of Doc.
  + UserTest now checks if user with Umlaut can be created properly.

## 2.1.0

+ ChangeTriggers API has changed. ChangeTriggers now have to return PoBox.
+ ChangeTriggers may stop further execution of commands, so for example
  if a validating trigger finds a problematic upload, it may prevent the data from being stored in the database
  and return a specific error message instead.
+ Added zipFolder API command to CmdInterpreter which can create a zipped folder structure and either append it
  to a new OSD or offer it for download.
    * Note.1: at the moment this works synchronously, so zipping incredibly huge or complex folder structures on insufficient
  hardware may create problems.
    * Note.2: be careful when dealing with large zip archives which contain more than 2^16 entries, as not all zip variants
  handle this correctly.
+ Added getConfigEntry and setConfigEntry API commands to CmdInterpreter.
+ Updated build.example.xml for new libraries.
+ Upgrade to Hibernate 3.6.8
+ Upgrade to commons-io 2.1
+ Upgrade to commons-codec 1.5
+ Upgrade to slf4j-1.6.4
+ Upgrade to logback-1.0.0
+ Upgrade to postgresql jdbc driver 9.1
+ Upgrade to support mysql jdbc driver 5.1.18
+ Upgrade to Grails 2.0 (and Groovy 1.8.4)
+ Upgrade to Lucene 3.5.0
+ New method to create filenames from osd.name: osd.createFilenameFromName(path).
+ Moved createZippedFolder-functionality from CmdInterpreter to Folder.
+ Fixed bug in session handling / disconnect command - if the session was not found, the disconnect command failed.
+ [from entitylib.changelog: ] Fixed bug in CinnamonIndexInitializer. Index items for folder.owner and folder.type
  would be created with string_indexer instead of integer_indexer.
  If you used the CII to create your initial index items, please update
  the two items by setting their indexer class to xpath.integer_indexer.
+ Fixed bug in WorkflowServer: in case of a database rollback, it did not re-initialize the EntityManager on
  the OSD-DAO correctly.
+ Fixed Hibernate session handling in IndexServer.

## 2.0.6

+ Change internals of Workflows: Tasks objects are now created with name $workflowName.$taskName.
+ All tasks are now connected (via Relation) to their workflow, not just the start task.
+ Transitions in Workflow are now only executed if they are ready. Previously, you could continue to
  click on already completed tasks, spawning ever more subsequent tasks regardless of the state.

## 2.0.5

+ Upgraded to Lucene 3.4.0
+ Fixed bug #2167 in translation extension: latestHead / latestBranch on older translation objects was not set correctly.
New Features:
+ The listGroups command will return the subgroups and users of each listed group
  (without descending into sub-subgroups).
+ Added cloneOn{Left,Right}Copy for RelationTypes.
*** API changes:
    * FormatManager.deleteFormat now has parameter format_id instead of formatid.
    * AclManager.addGroupToAcl now has parameters acl_id and group_id (added '_').
    * RelationTypeManager.createRelationType and parametrized constructors of RelationType no longer accept "1" and "0" for
      leftcopyprotectd and rightcopyprotected. Please use parameter strings "true" or "false" to set those flags via HTTP.
    * Use of reindex and clearindex command now requires superuser status (to prevent any registered user from issuing an
      hour-long running Lucene re-index action).
    * RelationTypeManager.deleteRelationType now expects parameter "id" instead of "relationtypeid".
    * UserManager.deleteUser now expects parameter user_id instead of userid.
    * GroupManager.addUserToGroup and GroupManager.removeUserFromGroup now use user_id and group_id instead of
      groupId and userId (for more consistency)


## 2.0.4

+ Fixed workflows. Workflows tasks now have all configuration data in their metadata instead of being split up
  into content xml and metadata xml. The demo workflow has been updated to reflect those changes.

## 2.0.3

+ Added "echo" API command.
+ Added autocreate parameter to getFolderByPath API command.

## 2.0.2

+ Fixed forkSession to no longer return the original ticket.
+ getUsersPermissions returns listPermissions' output for superusers.
+ Improved session handling (do not load session twice when using session based logging)

## 2.0.1

+ Improved session-based logging.
+ Added forkSession command.
+ Copy and version command now will run newObject.lifeCycleState's enterState method.
+ Added searchSimple - a search method that accepts a simple query string instead of xml query objects.

## 2.0.0

+ Increased version number to reflect sum of changes over the last months. #like-a-kernel-dev

## Old changes
    
    1.0.14
    + Added auto-initialize as repository related config field.
    + Added AutoInitializer which initializes empty repositories.
    + Added initialize-tests as repository related config field.
    
    1.0.13
    + Upgraded to Lucene 3.3.0
    
    1.0.12
    + Fixed bug in paged search_results (Exception was thrown with empty result set)
    + ContentContainer now loads content only when needed.
    + File upload now uses streaming API so large uploads will not run OOM
    + Added getLifeCycleState(id) to API
    + Changed naive password encryption to improved method using Damien Miller's jBcrypt.
    + Added optional Parameter "name" to startRenderTask(...)
    
    1.0.11
    + Fixed bug in LifeCycleState which could lead to infinite recursion in equals().
    + Updated to Lucene 3.1.0, currently using deprecated API
    + Fixed bug #2015 - named query findLifeCycleByName now works.
    + RenderServer now uses lifecycle state instead of procstate field in renderTask OSD to show status.
    + LifeCycleAPI extended: getLifeCycle(id)
    + Added LifeCycleManager extension to manage LifeCycles and LifeCycleStates by API.
    
    1.0.10
    + Fixed internal LifeCylceState-API
    + Added ChangeAclState LifeCycleState (see: http://cinnamon-cms.de/cinnamonserver/components-and-concepts/lifecycles )
    
    1.0.9
    + Added sudo API method
    + Indexing speed improved by up to 300%
    
    1.0.8
    + Improved error handling: trying to solve all cases of Null-Response errors.
    + Changed many exceptions from RuntimeException to CinnamonException with better message texts.
    + Added RenderServerConnector-extension.
    + Created module CinnamonBase.
    + Refactored CinnamonClient lib so it is no longer is dependent on entitylib and utils.
    + Change locking in LuceneBridge - index system is locked for shorter periods of time (about 15% of previous value). This
      should help to prevent deadlocks / thread starvation.
    + Fixed bug 1995: wrong repository name will now result in appropriate error message.
    
    1.0.7
    + setMetadata has changed API. It can now return warnings in addition to a success message.
    + fixed #1929 which was caused by OSD still using "latestbranches" in one place.
    + fixed potential synchronisation-issue in API method search().
    + fixed bug in getSysMeta - values for fields containing user objects did not return correct values.
    + new: searchFolders / searchObjects now includes parentFolders in output.
    + fixed: searchFolders now really does support paged results.
    + added RelationResolvers: FixedRelationResolver, LatestHeadResolver, LatestBranchResolver. Relations are updated
      automatically (if configured) to return the correct version of the related OSDs. Note: this update requires you to
      add new ChangeTriggers/Types for FixedRelationResolver as well as the Resolvers to an existing database.
      See: http://cinnamon-cms.de
    + removed API methods queryFolders and executeXmlQuery for security reasons (and because they are not used by the client).
    
    1.0.6
    + OSD.latestbranches was renamed to OSD.latestbranch (both the field and the db-column)
    + Allow disconnect() even if the session has expired.
    + improved test for copyAllVersions
    
    1.0.6_RC1
    + Added UI-Languages: You should create the basic languages (zxx, und, mul) in the new ui_languages table
      which is identical to the languages table. UI-Language entries are used to translate stuff like
      object type names which appear in the user interface. This new feature allows you to separate the
      languages of your content documents from the languages of the user interface.
    + API of createFolder changed: the method now returns the serialized folder as XML.
    + Fixed bug in ContentStore class which would cause upload / copy operations to fail if it tried to create an
     existing folder.
    + Relations now have metadata field. Added metadata as optional parameter to createRelation.
    + getRelations now has optional parameter include_metadata which defaults to true.
    + Added getFoldersById
    + createTranslation now indexes new objects
    + copyContent now sets the target object's format.
    + createTranslation adds slightly different xml to metadata:
      <metaset type="translation_extension"> instead of <translation>
    + CmdInterpreter.deleteAllVersions now deletes the object first and then updates the Lucene index (safer this way).
    
    1.0.5
    + Serialized folder now contains element <hasChildren> with value true or false, depending on whether the folder has
    sub-folders.
    + refactoring of internal repository handling.
    
    1.0.4
    + Fixed #1776 Multiple repositories and extensions could prevent server startup.
    + Fixed #1771 Files are only deleted, if no database rollback occurs while the server
      is working on the current request.
    
    1.0.3
    (see http://cinnamon-cms.de/cinnamonserver/upgrade/upgrade-v1.0.3 for upgrade instructions)
    + Added <categories>...</categories> to cinnamon_config.xml, so available categories of server functions
      may be declared for a repository. For example, a repository may have <category>LifeCylce</category>
       functionality which the client can detect in this way.
    + Added version information to list of repositories. #1762
    + Added lifecycle-extension, part 1 with method listlifecycles
        Database changes:
    #################################################
    ---    table lifecycle_states
    
    CREATE TABLE lifecycle_states
    (
      id bigint NOT NULL,
      "name" character varying(128) NOT NULL,
      parameter text NOT NULL,
      state_class character varying(128) NOT NULL,
      lifecycle_id bigint NOT NULL,
      CONSTRAINT lifecycle_states_pkey PRIMARY KEY (id),
      CONSTRAINT fke3bd9877407f648b FOREIGN KEY (lifecycle_id)
          REFERENCES lifecycles (id) MATCH SIMPLE
          ON UPDATE NO ACTION ON DELETE NO ACTION,
      CONSTRAINT lifecycle_states_name_key UNIQUE (name)
    )
    WITH (
      OIDS=FALSE
    );
    ALTER TABLE lifecycle_states OWNER TO cinnamon;
    
    #################################################
    ---    table lifecycles
    
    CREATE TABLE lifecycles
    (
      id bigint NOT NULL,
      "name" character varying(128) NOT NULL,
      default_state_id bigint,
      CONSTRAINT lifecycles_pkey PRIMARY KEY (id),
      CONSTRAINT fkd1620649b3d60a9d FOREIGN KEY (default_state_id)
          REFERENCES lifecycle_states (id) MATCH SIMPLE
          ON UPDATE NO ACTION ON DELETE NO ACTION,
      CONSTRAINT lifecycles_name_key UNIQUE (name)
    )
    WITH (
      OIDS=FALSE
    );
    ALTER TABLE lifecycles OWNER TO cinnamon;
    #################################################
    
        add column lifecycle_state_id(bigint, nullable=true) to objects [foreign key to lifecycle_states]
        
    + searchObjects and searchFolders now accept page_size and page parameters for pageable results.
    + add total-results attribute to search result documents.
    + calling the server page by GET method without parameters no longer shows the complete repositories
     node from config file but a simple XML document with //repositories/repository/name.
    + Fixed #1722 and #1716 (setting a folder's parent to illegal values).
    + Fixed #1714 (Lucene index of folder content will be updated if a folder is moved).
    + Reduced log spam by IndexServer if nothing relevant happens (see docs for new lucene.properties field "logModulus").
    
    1.0.2
    + Added setPassword API method to allow a logged in user to change his or her password.
    + Added MailSender class for an easy way to send mails from Cinnamon.
      You should extend your configuration file by the following fields:
        <minimalPasswordLength>4</minimalPasswordLength>
        <system-administrator>_your_admin's_email_address_</system-administrator>
      Also you should copy (and translate / adjust) the files in Server/dist_config/cinnamon_system to your
      server's system_root. 
      Otherwise, your system will be missing the templates for password reset and mail verification.
    
    1.0.1
    + createWorkflow needs the Permission.CREATE_INSTANCE.
    + Added transition CancelReviewWorkflow for test workflow.
    + WorkflowServer now watches for tasks with deadlines which have not been met 
     and executes the configured deadline_transition on them.
    + WorkflowServer finds workflows which have an unmet deadline and creates a 
     deadline-Task for them.
    + Increased stability of WorkflowServer (crash of transition does not take down
     the whole thread any more).  
    
    1.0.0
    + removed obsolete API-methods getExtension, clearMessage, readMessage.
    + updated Lucene to version 3.0.1
    + refactoring of RepositoryHelper
    + fixed bug in TestTrigger. 
    + fixed bug in Translation extension (mistakenly created new EntityManagers for translations). 
    + fixed leak of database connections (which would cause server to simply halt without
     warning or indication of what went wrong).
    + removed RepositoryHelper
    + started reworking the WorkflowEngine; added: initializeWorkflows
    + added apiClass: server.extension.WorkflowApi
    + added API command findOpenTasks
    + added API command doTransition
    + added simple review workflow + tests
    + added workflow server class which handles automatic transitions for server side workflow tasks.
     
    0.7.1
    + Added support for MySQL in build.example.xml.
    + Moved queryCustomTable into its own extension class. If you need this method, you should add
    <apiClass>server.extension.QueryCustomTable</apiClass> to your cinnamon_config.xml.
    + changed all API methods to deliver XML responses, where appropriate.
    + moved createUser and deleteUser into server.extension.UserManagement.
    + moved all methods which require administrative priveleges into extension classes.
        *** You will need to adjust your cinnamon_config.xml before you can run any tests! ***
        see cinnamon_config.example.xml for more details.
    + Fixed bug in version command.
    
    0.7.0
    + Enabled encrypted passwords. Just add <encryptPasswords>true</encryptPasswords> to your
      cinnamon_config.xml to have all new Accounts have their passwords stored in encrypted form.
      Note: old passwords are not converted. The default password-salt is "0xCHICKENSOUPx0",
      so you can convert old passwords by using:
        echo "my_old_password0xCHICKENSOUPx0" | sha256sum
      You can set a better salt (recommended) with <passwordSalt>...</passwordSalt> in your
      cinnamon_config.xml. If you change your salt, you will need to generate new passwords. 
    + Removed obsolete tests.
    + Refactoring for speed: searchObjects now takes only 50% as long to return a resultset.
    + Fixed bug in session expiration code.
    + (from EntityLib): Added IndexItem.search_condition. This is an XPath expression which has to evaluate to true on one of (sysmeta, content, metadata) before
    an item can be indexed. Please add a text column "search_condition" with the same size and parameters as index_items.search_string and (if possible) 
    default value "true()".
    
    0.6.9
    Moved initializeDatabase() into its own extension class, server.extension.Initializer. If you
    need this functionality, please add <apiClass>server.extension.Initializer</apiClass> to your
    repository.
    
    Added extension TransformationEngine with 3 methods:
        - transformObject (transforms the content of an object)
        - transformObjectToFile (returns a file response with the transformed content)
        - listTransformers (lists the available transformers as XML)
    To enable the TransformationEngine, add <apiClass>server.extension.TransformationEngine</apiClass>
     to your repository in cinnamon_config.xml
        
    Added Xhtml2PdfTransformer as an example of a transformation plugin. 
        This creates a new dependency for EntityLib (on xhtmlrenderer.dev.java.net a.k.a. Flying Saucer)
        To enable this transformer, add a new Transformer to the database with:
            name = "pdfTestTransformer" or something like it.
            description = "Transform xml/xhtml to pdf"
            transformer_class = server.transformation.XhtmlToPdfTransformer.class
            source_format_id = id of xml format
            target_format_id = id of pdf format
    
    0.6.8
    Moved some more classes to remove cyclic dependency.
    Added Transformer entity. Please update your database and persistence.xml
     (see EntityLib/changelog for more information).
    
    0.6.7
    Moving some classes to Utils. See changelog there.
    
    0.6.6
    + createTranslation now has an optional parameter target_folder_id.
    + added extension method checkTranslation
    + fixed bug in createTranslation (which could cause missing predecessor links on empty nodes)
    + The IndexServer is now disabled by default. To enable it, add <startIndexServer>true</startIndexServer> 
        to your	cinnamon_config.xml.
    + added ChangeTriggers. 
        Update your Database (see EntityLib/changelog for more information).
        Update your persistence.xml.	
    
    0.6.5
    + Created server.extension package for independent extensions to the CinnamonServer API
    + Added Translation extension with API-method createTranslation. To activate the extension,
        add <apiClass>server.extension.Translation</apiClass> to the repository in the config.xml.
    
    + fixed bug where new non-branch object was not set to latestHead (#1500)
    + connect() parameter language_id was removed, parameter "language" was added - you
      can now define the session's language by ISO-code.
    + Session-table is purged after server reboot. #1489
    + RepositoryHelper now checks if the session has expired and if not extends it by
       //repository/sessionExpirationTime (default: 1 hour)
    + connect() now always verifies password case sensitive.
    + fixed permission checking on getObject/getObjectById/() #1497
    
    0.6.4
    new API-Methods:
    listLanguages()
    listMesssages()
    
    From EntityLib:
    I18N: Names and descriptions are now returned localized by the server, if a translation exists.
    Moved server.Language to server.i18n.Language. Update your persistence.xml!
    Moved the 3 Boolean-Columns for_content, for_metadata, for_sysmeta from table index_types to index_items.
    You should update your database layout accordingly.
    By limiting an IndexItem to a specific type of data (for example, only metadata), you may experience
    some speedup during indexing.
    
    Added Column Boolean objects.index_ok Default NULL.
    Added Column Boolean folders.index_ok Default NULL. 
    Behaviour of IndexServer can now be configured via lucene.properties:
    - itemsPerRun (new)
    - sleepBetweenRuns (new) - milliseconds
    - indexDir
    IndexServer is now more robust and should survive broken input data.
    
    Fixed bug: did not catch delete operation on non-existent object/folder.
    Fixed broken Tests.
    
    API changed:
    createPermission & getPermission now wrap the permission element in "<permissions>...</permission>".
    OSD.contentPath is now stored without full path information (just the path below $repository),
    that is: /home/zimt/cinnamon-data/cmn_test is omitted.
    
    0.6.3
    Improved build.example-Script.
    EntityLib:
        Added RegexQueryBuilder to Lucene-XML-Query-Parser.
        Fixed ParentFolderPathIndexer
        Fixed bug in FolderDAOHibernate.getParentFolders().
        Fixed problematic behaviour in LuceneBridge (UTF-8-problem on Windows) 
    
    0.6.2
    Updated to Lucene 3.0.0
    Added WildcardQueryBuilder
    Added ReverseStringIndexer and ReverseCompleteStringIndexer
    Added ParentFolderPathIndexer
    
    0.6.1
    Superusers are exempt from permission checks - changed getObjects* to reflect this.
    Added API: getObjectsWithCustomMetadata to return objects inside a folder with their metadata.
    Added CompleteStringIndexer, which does not tokenize Strings but indexes them as complete terms.
    
    0.6.0
    Temporary: Changed indexer: ,.-/ are allowed inside words to enable indexing of serial numbers etc. 
    Fixed two concurrency bugs which would cause threads to hang.
    Make sure that OSD.contentSize is nullable in your database.
    Make sure that OSD.contentPath is nullable in your database.
    The default of contentSize and contentPath is null, as 0 may be a valid file size.
    
    0.5.9
    Fixed bug which filtered all Folder objects in searchFolders. D'oh.
    
    Changes to database schema: make all fields NOT NULL, if they MUST have a value.
    OSD:
    contentpath=nullable
    contentsize=nullable
    name=NOT NULL
    creator=NOT NULL
    modifier=NOT NULL
    modified=NOT NULL (default == created)
    language, metadata, latesthead, latestbranches, version=NOT NULL
    type_id=NOT NULL (an object MUST have a type!)
    
    Group:
    is_user(default:false), name, description = NOT NULL,
    
    RelationType:
    leftobjectprotected, rightobjectprotected, retainoncheckin = NOT NULL
    
    Add column obj_version (Long, nullable default 0)to tables:
    acls
    customtables
    aclentries
    aclentry_permissions
    folder_types
    formats
    groups
    group_users
    index_groups
    languages
    metadataindex (as long as it is needed...)
    objtypes
    permissions
    relationtypes
    sessions
    
    
    0.5.8
    "_" is now considered a letter for the Lucene analyzer.
    Added API methods searchObjects and searchFolders, which return the search results as XML objects/folders.
    Added: API method listindexgroups, getindexgroup(id)
    New: IndexGroup
    Add a table index_groups with (id bigint primary key, name nvarchar unique).
    Add a row "_default_index_group" to table index_groups.
    Add a column index_group_id (bigint) to index_items.
    Set this column to the id of the _default_index_group and change it to NOT NULL.
    Add a foreign key constraint from index_group_id to index_groups.id.
    
    New: FolderType
    Add a table folder_types which has the same columns and constraints as object_type.
    Add a row with name "_default_folder_type" to folder_types.
    Add a column folder_type_id to folders.
    Set this column to the id of the _default_folder_type and change it to NOT NULL.
    Add a foreign key constraint from folder_type_id to folder_types.id.
    
    Added API methods createFolderType, getFolderTypes and deleteFolderType.
    Note: if createFolder does not receive a param typeid, it will use the default.
    
    Added IndexServer which runs in a new thread per repository (once a call to the server was made which 
    causes CmnServer to initialize).
    Added API-method clearindex.
    Fixed HibernateSession-related problems in handling of EntityManager.
    Switched from lucene-2.4.1 to lucene-2.9.0
    Heavy refactoring in CmdInterpreter and CmdServer: Cinnamon can now load new customized APIs on startup.
    You need to add the following to each normal Cinnamon repository element in the cinnamon_config.xml:
            <apiClasses>
               <apiClass>server.CmdInterpreter</apiClass>
            </apiClasses> 
    otherwise your repository will be unusable.
    
    Added CinnamonIndexInitializer which helps to make Lucene indexing/searching work.
    Moved DefaultIndexer to server.index.indexer.DefaultIndexer
    SearchResults are now filtered according to ACL-Permissions (BROWSE_OBJECT, BROWSE_FOLDER)
    Installation hint: if you get an error:
    "java.io.FileNotFoundException: no segments* file found in org.apache.lucene.store.FSDirectory@/home/zimt/cinnamon-system/index/cmn_test:"
    it could be that your Tomcat-temp-folder is not writable for lucene.
    On Ubuntu, that is /tmp/tomcat6-temp, unless configured otherwise.
    
    Added API method search which enables searching for indexed objects and folders.
    Modified several methods to index new objects, update changed ones and delete old ones.
    
    Add an UTF-8 column "fieldname" NOT NULL varchar 255 to index_items, default: "content"
    
    Please change the column name index_items.xpath in the db to "index_items.search_string". 
        RFU (Reason For Update): An IndexItem's search string does not need to be an xpath
        statement, sometimes this field will be just a string, depending on the indexer class.  
    
    0.5.7
    Added API method listIndexItems. It will return an answer like:
    
    
    Added API method queryFolders.
    createFolder now requires an additional parameter (ownerid). The owner of
    a folder may no longer be NULL.
    
    Database changes: (see upgrade.txt for details)
    create table index_types:
    
                   Table "public.index_types"
          Column       |          Type          | Modifiers
    -------------------+------------------------+-----------
     id                | bigint                 | not null
     data_type         | character varying(255) | not null
     for_content       | boolean                | not null
     for_metadata      | boolean                | not null
     for_sysmeta       | boolean                | not null
     indexer_class     | character varying(255) | not null
     name              | character varying(128) | not null << must be unique
     obj_version       | bigint                 | not null
     va_provider_class | character varying(255) | not null
    
     
    Indexes:
        "index_types_pkey" PRIMARY KEY, btree (id)
    -----------------------------------------------------------------------
    
    Insert the default xpathIndexer into index_types:
    
     id | data_type | for_content | for_metadata | for_sysmeta |        indexer_class        |     name     | obj_version |              va_provider_class
    ----+-----------+-------------+--------------+-------------+-----------------------------+--------------+-------------+----------------------------------------------
     52 | string    | t           | t            | t           | server.index.DefaultIndexer | xpathIndexer |           0 | server.index.valueAssistence.DefaultProvider
    [Note: as of 0.5.8 it's server.index.indexer.DefaultIndexer]
    
    -----------------------------------------------------------------------
    create table index_items:
    
                    Table "public.index_items"
          Column      |            Type            | Modifiers
    ------------------+----------------------------+-----------
     id               | bigint                     | not null
     multiple_results | boolean                    | not null
     name             | character varying(128)     | not null << must be unique
     obj_version      | bigint                     | not null
     va_params        | character varying(2097152) | not null
     index_type_id    | bigint                     | not null
     system_index	  | boolean			           | not null
     search_string    | text					   | not null
      
    Indexes:
        "index_items_pkey" PRIMARY KEY, btree (id)
    Foreign-key constraints:
        "fk63f40e13f4e29618" FOREIGN KEY (index_type_id) REFERENCES index_types(id)
     
    --------------------------------------------------------------------------------
    
    initializeDatabse now creates a default indexType.
    
    Fixed #1184 - Windows client received XML without proper encoding set in HTTP-header.
    Updated build.example.xml to include Lucene.
    Fixed updateFolder to properly update owner.
    getSubfolders(rootFolder) now should no longer include the rootFolder in its results.
    
    0.5.6
    getFolder() now always returns all ancestors.
    getFolderByPath() now returns root folder.
    getRelations now returns an error message if the leftId- or rightId-part of 
     a relation cannot be found.
    
    new: removeUserFromGroup(userId,groupId)
    Removed getFolderById - use getFolder instead; params are the same.
    Metadata must be XML.
        conversion note: you can still read the old metadata, but if you want to set it again
        on an object or folder, you have to make sure it is valid xml. Otherwise, an error is
        returned.
    User.asElement now adds isSuperuser-element to XML document.
    If a folder has an owner, Cinnamon checks if meta-group _owner has a corresponding ACL-Entry. 
    getRelations now returns a XML response.
    
    0.5.5
    changed: getObjTypes now returns XML list.
    new: getFolderMeta
    OSD now inherits parentFolder's ACL unless told otherwise.(TargetFolder.acl on copy)
    updated NamedQueries (in server.Acl)
    updated javadoc
    createAcl now returns /acls/acl instead of /acl
    new: getUserByName(param: name)
    new: getUsersPermissions(param: userId=user.id, aclId=acl.id))
    
    listGroups now returns /groups/group instead of /cinnamon/group
    getAclEntries now returns /aclEntries/aclEntry instead of /aclentry
    createRelation now returns /relations/relation instead of /cinnamon/relation,
        and it uses elements instead of attributes.
    
    added getFolder, which returns a somewhat big XML doc.
    setSysMeta and getSysMeta are no longer usable with parameter folderId (was
     unused in client and problematic API design anyway)
    Unified output of getObjects, queryObjects, getObject, getObjectsById.
    Removed RepositoryHelper.addObjectResultSetToOutput()
    Removed API method findObjectByName
    
    Added sql queries in xml format. To use them, create the file
    sql.properties with a property "sql.xsl.filename" which contains the complete
    path of the stylesheet in docs/xml2sql_2.xsl (or whatever stylesheet you use).
    The command is executeXmlQuery and expects as parameter "xml" a XML query document.
    A short format description can be found in: docs/xml2sql.txt
    An example file can be found in the CinnamonClient (Safran/testdata/xml_sql_test_input.xml)
    
    -----------------------------------------------------------
    0.5.4
    Fixed #1123 getRelations was broken.
    Extended getSysMeta. Additional possible params:
        owner_id, modifier_id, creator_id and objtype_id for objects,
        owner_id and owner for folders.
    
    If getSysMeta encounteres a null value for the requested parameter, it returns an xml message with:
    <error>error.result_value_is_null</errror>
    New API method: queryObjectsXML
     
    fixed bugs.
    Important: getSysMeta and setSysMeta now expect 'acl_id' instead of 'permissionid' as parameter name.
    See EntityLibs.changelog for changes regarding the new Language field on OSDs and 
     other database related changes.
    -----------------------------------------------------------
    0.5.3
    SqlConn and SqlCustomConn now throw CinnamonExceptions by wrapping the original
    exception instead of just throwing up ex.getMessage(). 
    
    added docs/permissions.csv - a list of basic permissions
    
    moved cinnamon_config.xml to Server/. - you should set CINNAMON_HOME_DIR as an
    environment variable and keep this file outside of the war.
    (NOTE: set as a system environment variable in Windows)
    
    Example: CINNAMON_HOME_DIR=C:\cinnamon
    Config file: C:\cinnamon\cinnamon_config.xml
    
    src/log4j.properties turned into an example file.
    please supply your own version of log4j.properties, as appropriate.
    
    -----------------------------------------------------------
    0.5.2
    - deleteGroup now returns <result><value>true</value></result> 
    or an exception.
    - createGroup now returns <cinnamon><groupId>$value</groupId></cinnamon> 
    or an exception.
    - copy changed parameters to "sourceid" and "targetfolderid"
    
    Methods do no longer return "F" for a failed action, 
    but the actual error message instead.
    getUser does not return a "nil"-user if no User was found, but an error message.
    
    -----------------------------------------------------------
    0.5.1
    fixed bug in createFolder which would swallow RuntimeExceptions.
    
    ----------------------------------------------------------
    0.5.0
    new Cmds:
    createPermission
    getPermission
    listPermissions
    deletePermission
    
    API changes:
    createFolder now expects aclid instead of permissionid.
    addGroupToAcl: new output format
    
    
    -----------------------------------------------------------
    0.4.5: createAcl now uses XML to communicate its results.
    -----------------------------------------------------------
    0.4.4:
    Added permissions to AclEntries.
    InitializeDatabase now creates default-Permissions.
    You will need to add the new Permissions to the AclEntryPermissions table.
    This version is dependent on EntityLib-0.4.5
    
    Validator now checks existence of AclEntries for "_owner" and "_everyone"-Default Groups.
    -----------------------------------------------------------
    0.4.3: 
    added listAclMembers(aclId)
    -----------------------------------------------------------
    0.4.2:
    group_users now has an artificial Primary Key (id). Please update the table accordingly.
    -----------------------------------------------------------
    0.4.1
    added Xstream as dependency (as an experimental Utility-Lib to create XML-versions of Entity-classes)
    http://xstream.sf.net (basically you need xstream*.jar and xpp3*.jar) 
    -----------------------------------------------------------
    0.4.0:
    Database: customtables needs a varchar(255) column "jdbcdriver". 
    Database,API: CustomTable now uses ACLs instead of permissionId. This means that a new FK-constraint is 
    needed between CustomTable.acl_id and server.Acl.
    
    Config: <repository>-Tag updated:
        <repository>
            <name>cmn_dev</name>
            <persistence_unit>cinnamon</persistence_unit>
        </repository>
    This allows us to have multiple databases with different persistence units (for example, use a non-updating
    persistence unit for production data).
    
    API: changed Relations.left to Relations.leftOSD and Relations.right to Relations.rightOSD
        because "left" and "right" are reserved words in SQL and thus likely to cause trouble with HQL
    API: createRelation now has a XML response. Please adjust your client code, if necessary.
    Config: Repositories in cinnamon_config.xml need their full name now (eg, cmn_dev instead of dev).
