
CouchDB Mailer Extension
========================

CouchDB Mailer Extension allows your CouchDB applications to send emails.


Build from Source
-----------------

1.  Get Git repository:
        git clone git://github.com/jri/couchdb-mailer.git
    A directory `couchdb-mailer` will be created

2.  Compile and build:
        cd couchdb-mailer
        ant
    The file `dist/couchdb-mailer-0.1-SNAPSHOT.jar` will be created


Download Binary
---------------

<http://cloud.github.com/downloads/jri/couchdb-mailer/couchdb-mailer-0.1-SNAPSHOT.jar>


Installation
------------

1.  Setup Java libraries:

    1.1. Put `couchdb-mailer-0.1-SNAPSHOT.jar` to a directory where you store java libraries  
        *IMPORTANT*: this directory must be readable by the user which runs the couchdb process (usually user `couchdb`).

    1.2. Put the following 3rd party libraries to the same directory:
            mail-1.4.2.jar
            activation-1.1.1.jar
            json-lib-2.3-jdk13.jar
            commons-beanutils-core-1.8.0.jar
            commons-collections-3.2.1.jar
            commons-lang-2.4.jar
            commons-logging-1.1.1.jar
            ezmorph-1.0.6.jar

2.  Configure CouchDB: add lines to `/etc/couchdb/local.ini`

        [external]
        mailer=/usr/bin/java -server -jar /path/to/couchdb-mailer-0.1-SNAPSHOT.jar

        [httpd_db_handlers]
        _mailer = {couch_httpd_external, handle_external_req, <<"mailer">>}

3.  Restart CouchDB


Usage
-----

From your application send a POST request to

    http://www.your-couchdb-host.com/your-couchdb/_mailer

Put the mail content (recipients, subject ...) in the POST request's body.


------------
Jörg Richter  
Oct 12, 2009
