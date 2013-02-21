# Orphan-Finder

Task: Find orphaned files and send them to the happy ever-after in /dev/null

If a server is shutdown unexpectedly in the last stages of removing some content,
there exists a chance that content files may be left lying around in the
file system. This class is responsible for finding the orphaned files and
removing them from the repository storage area.

## Usage

Command line parameters: path to config.properties file.

Start the Orphan-Finder on the server with:

    java -jar -Dlogback.configurationFile=logback.xml orphanFinder.jar config.properties

The logback configuration file is optional. It may help reduce the deluge of
log messages that otherwise results.

You should have a valid cinnamon_config.xml in CINNAMON_HOME_DIR for the process to find.

## Config.properties content:

	# URL of the Cinnamon 2 server:
	server.url=http://cinnamon.test:8080/cinnamon/cinnamon
	
	# the repository that needs cleaning up:
	default_repository=cmn_test
	
	# Name and password of administrator account:
	server.password=admin	
	server.username=admin
	
	# if dryRun is true, just simulate the deletion
    dryRun=true

## Building

* create the cinnamon_full.jar with the command "ant makeFullJar" in the cinnamon2-server directory.
* copy the jar to the cinnamon2-tools/lib folder.
* in cinnamon2-tools run the command "ant makeOrphanFinderJar"

If you get an error message "java.lang.SecurityException: Invalid signature file digest for Manifest main attributes",
please delete the files 

	/META-INF/BECKY.DSA
	/META-INF/BECKY.DSA
	/META-INF/BECKY.SF
	/META-INF/BECKY.SF
	
from the cinnamon_full.war (they may be included by the BouncyCastle crypto lib)


 
