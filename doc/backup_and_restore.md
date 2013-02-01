# Backup and Restore

Status: 2013-02-01 #1

This document describes how to backup your Cinnamon data and restore it in case of system failure.

## Precautions

Currently, you should either run the backup while no user is logged in (for example, at night)
or when the application server (Tomcat 7) is shut down. 

## Backup

Cinnamon data is stored in two places: your database and the filesystem. Both need a backup strategy.
The simplest way to do this is:
	
	# example for a Linux with Postgresql 9 and Tomcat 7
	
	## stop application server so user's do not make any changes while the backup is running.
	sudo service tomcat7 stop
	
	## backup database:
	# for each repository (substitute "demo" for you repository name in both places):
	sudo -u postgres pg_dump demo > demo.sql
	
	## backup filesystem:
	tar cvzf cinnamon_data.tar.gz $CINNAMON_HOME_DIR/cinnamon-data
	
	# restart application server:
	sudo service tomcat7 start
	
Then store the sql files and the cinnamon.tar.gz in your backup file system.

## Restore

To restore the data, 

* stop the application server (Tomcat 7)
* unpack the the cinnamon-data files in the Cinnamon data directory.
* for each repository, create the database with user cinnamon (as during installation)
* import the data to the new database: 

	sudo -u postgres psql demo -f demo.sql

* restart the application server

Notes: 

* you should make sure that the permissions on the recreated files are correct.
* you should also create one backup of all files in the CINNAMON_HOME_DIR along with all binary files (cinnamon.war etc), in case you need to install Cinnamon from scratch.
