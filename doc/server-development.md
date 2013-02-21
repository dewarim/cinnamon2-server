# Server development

Note: OSD is the abbreviation for ObjectSystemData, a class which represents an object in a Cinnamon repository.
OSDs may have content (documents, images) which is stored in the file system. They are the central objects of Cinnamon.

## Component overview

The following is a short overview of the libraries which together represent the Cinnamon 2 server.
For more details, have please check out the source code.

### cinnamon2-base

This library provides some base classes for all cinnamon2 modules:

* CinnamonException: extends RuntimeException with localization / message  options.
* Constants: Constants used everywhere, for example the name of the default object type.
* PermissionNames: Names of permission used to control access / actions on OSDs and folders.
* ContentReader (reads OSD content from the file system)

### cinnamon2-utils

This library provides utlity classes:

* jbcrypt-code
* Hibernate session handling
* classes to create several kinds of server responses (for example, XML or File responses)
* configuration classes

### cinnamon2-entitylib

This module contains the domain classes which provide access to all data and data types of Cinnamon.
It also houses the DAO classes (Data Access Objects) and the Apache Lucene based indexer.

If you want to make changes to the database layout, you can get a glimpse of the features by
looking at server.FolderType and server.dao.FolderTypeDAOHibernate. Named queries are stored
in the server.Acl class for historical and technical reasons.

### cinnamon2-humulus

Humulus is the base library for Dandelion and Illicium, both Grails applications.
It is the counter-part to cinnamon2-utils in a Grails setting, handling per-request database-connection 
switching and providing Login- and Logoutcontrollers based on the Grails Spring Security plugin.

### cinnamon2-dandelion

The administration tool for Cinnamon is a Grails application which provides a GUI to manage
your Cinnamon installation on a per-repository setting. If you want to provide management capabilities
for new features, you can use the ObjectTypeController as an example.

Dandelion uses the domain model provided by cinnamon2-entitylib and adds controllers and views
as part of the MVC-pattern. It has a single persistent domain class (LifecycleLog) which can be
used by the Cinnamon server to log lifecycle changes ("Who has set the document to published state?".
The LifecycleLog table may be stored in the same database as the other repository data or use 
a separate database connection for security reasons.

### cinnamon2-illicium

The Grails application Illicium is the first version of the webclient and as such a rather 
experimental piece of software. It is currently no longer under development. 
The new webclient will be part of Cinnamon 3.

### cinnamon2-renderserver

The Cinnamon Renderserver polls the Cinnamon main server for new render tasks and if one is found,
it will start an external process to handle the request. The Renderserver is used to create PDFs
by running the DITA Open Tooklit on the server by using an automated desktop client.

### cinnamon2-server

The main server library and host of the client-facing API. Here you will find the main build.xml
to create the cinnamon.war, as well as the API's workhorse: the CmdInterpreter, which handles all
incomming POST requests. If a command is not directly registered with the CmdInterpreter, it will
be forwarded to the appropriate API extension class.

### cinnamon2-clientlib

Safran, the Cinnamon2 client library, is a Java client which directly communicates with the server
and can be used to test API features as well as provide a programmatic approach to using the API.

The main class is [safran.Client](https://github.com/dewarim/cinnamon2-clientlib/blob/master/src/safran/Client.java)
which is in some way a client side mirror to the CmdInterpreter provided by the cinnamon2-server
module. The Client class contains some intelligence so it can parse responses and return object ids
as long values etc.

## Server extension and customization

### API extensions

If you want to extend the server's API, look for the server.extension.LinkApi in cinnamon2-server 
for an example of how new commands can be implemented. 
To activate a new API module, add an element like

       <apiClass>server.extension.LinkApi</apiClass>

to your repository configuration in the cinnamon_config.xml and deploy your new cinnamon.war
to your application server.

### Index Items

Index classes are responsible for extracting information from OSDs (sytem and custom metadata, 
file content) and storing it as Lucene documents. The indexer is given the content and metadata
along with an XPath expression (if any) to extract the relevant data.

See the [DefaultIndexer class](https://github.com/dewarim/cinnamon2-entitylib/blob/master/src/server/index/indexer/DefaultIndexer.java)
for more information. Indexers can be found in cinnamon2-entitylib.

### Change Triggers

ChangeTriggers are called before and after a request is processed by the server. The 
[RelationChangeTrigger](https://github.com/dewarim/cinnamon2-entitylib/blob/master/src/server/trigger/impl/RelationChangeTrigger.java)
is responsible for updating any dynamic relations (for example, if a user deletes the newest
item, all relations which point to it have to be checked if they should be updated to
point to the predecessor instead).

### Lifecycles

In case of a lifecycle state change, the server sends an event to the current and future state classes
associated with the lifecycle. A LifecycleState-class may perform operations on an object entering 
or leaving this state. An example is the [ChangeAclState](https://github.com/dewarim/cinnamon2-entitylib/blob/master/src/server/lifecycle/state/ChangeAclState.java) class which sets an object ACL to a new value according to its configuration.

### Workflows

What LifecycleState classes are to Lifecycles, Transition classes are to Workflows. When a workflow
task is finished, a Transition class is called and may verify the task as well as generate further
tasks and distribute them to their recipients. A task that fails verification is returned to the user
for correction. An example of a Transition class can be found in the server's test workflow:
the [StartToReview](https://github.com/dewarim/cinnamon2-server/blob/master/src/workflow/transition/test/StartToReview.java)
