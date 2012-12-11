# Cinnamon CMS

This is version 2.4.0 of Cinnamon, the enterprise content management system.

## Packages

* CinnamonPortable: Standalone edition for quick testing. Everything in one ZIP archive.
* Cinnamon_server_and_client: the complete binary package for installing in a Java webserver.
* Cinnamon-Server: just the server war files.
* Cinnamon-Client: the Windows Desktop Client

## Features

* Enterprise content management system. [Not a Web-CMS]
* multi-user authoring with robust permission handling
* document lifecycles
* workflow engine
* custom XML metadata
* semantic search and indexing with Lucene
* handles arbitrary content (Framemaker, DITA, Microsoft Word, Images etc)
* extract data from binary content with Apache Tika
* translation system for multi-lingual objects (version safe, including object relations)

## License

Cinnamon licensed under the LGPL v2.1 (http://www.gnu.org/licenses/old-licenses/lgpl-2.1.html). 
You may install and use this software without charge in commercial and non-commercial environments.

The Cinnamon server uses many open source components, which are licensed under their own,
 compatible licenses. For more details, see the [package license overview](http://cinnamon-cms.de/cinnamonserver/license) .

## Installation

For installation hints, visit the [Cinnamon Project Homepage](http://cinnamon-cms.de) and read the INSTALL.txt file.

If this is your first Cinnamon install, we recommend you download the Client zip here and 
the [Standalone Server-VM](https://sourceforge.net/projects/cinnamon/files/Server-VMs/), 
which is much easier to get up and running (of course we will try to help you if any questions or problems arise). 
Or alternatively, try the Cinnamon portable edition which does not require an install.

## Dependencies

### Server 

* Java 7 and a servlet container like Tomcat 7 or Jetty
* An RDBMS like PostgreSQL 9
* 1 GByte RAM available

### Client

* .NET-Framework 4.0

## Content

This release contains 

	CinnamonClient (CinnamonDesktop Client for MS-Windows with .NET 4)
	CinnamonServer:
		cinnamon.war (Cinnamon Server)
		dandelion.war (Administration tool)
		safran.full.jar (Java client library)
		essential configuration files
		demo.sql (PostgreSQL example Database)
		an example repository (cinnamon-data) with DITA files.
	INSTALL.txt (installation hints)

## Current maintainers

* Desktop Client: Boris Horner
* Server: Ingo Wiarda ( ingo.wiarda@horner-project.eu )

### Sponsor / Support

Development is sponsored by the [Horner GmbH](http://horner-project.eu), which
also provides commercial support and consulting for the Cinnamon CMS.

If you have questions regarding this project, need help with the installation or 
would like to offer feedback, please contact ingo.wiarda@horner-project.de