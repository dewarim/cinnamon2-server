echo "Clone all git repositories of Cinnamon Server v2 from Github."
  
# Renderserver: fetch tasks from a repository, execute them and upload the results.
git clone git://github.com/dewarim/cinnamon2-renderserver.git

# CinnamonBase: essential classes required by probably all Cinnamon code (interfaces, constants, exceptions)
git clone git://github.com/dewarim/cinnamon2-base.git

# CinnamonUtils: Utility classes (encryption, configuration handling, interfaces) 
git clone git://github.com/dewarim/cinnamon2-utils.git

# CinnamonEntitylib: Domain classes with JPA / Hibernate Annotations and Lucene indexing 
git clone git://github.com/dewarim/cinnamon2-entitylib.git

# CinnamonServer: the main server code
git clone git://github.com/dewarim/cinnamon2-server.git

# Dandelion: administrative Grails app for Cinnamon server.
git clone git://github.com/dewarim/cinnamon2-dandelion.git

# Illicium: Simple webclient in Grails for Cinnamon server.
git clone git://github.com/dewarim/cinnamon2-illicium.git

# Humulus: base plugin class for Grails based Cinnamon 2 projects (like Dandelion and Illicium) 
git clone git://github.com/dewarim/cinnamon2-humulus.git

# Cinnamon2-clientlib: Java client library for Cinnamon server.
git clone git://github.com/dewarim/cinnamon2-clientlib.git

# cinnamon-demo-data: DITA demo dataset and configuration files for Cinnamon server.
git clone git://github.com/dewarim/cinnamon2-demo-data.git

# Cinnamon2-Tools: Repository cleanup and conversion tools for Cinnamon.
git clone git://github.com/dewarim/cinnamon2-tools.git

echo "Finished cloning git repositories."
echo "To download the depenencies, use: 'wget http://download.horner-code.de/cinnamon/cinnamon-dependencies-2.4.1.zip'"
echo "Do not forget to set the environment variables:
    1. CINNAMON2_SOURCE: the path to this directory. 
    2. JAVA_LIB_HOME: the path to the directory which contains the unpacked dependencies-zip
    3. GRAILS_HOME: the path to your Grails install, at least version 2.1.1 is required for the Grails based modules."
