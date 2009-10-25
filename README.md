
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
    The file `dist/couchdb-mailer-0.2-SNAPSHOT.jar` will be created


Download Binary
---------------

<http://cloud.github.com/downloads/jri/couchdb-mailer/couchdb-mailer-0.1.jar>  
<http://cloud.github.com/downloads/jri/couchdb-mailer/couchdb-mailer-0.2-SNAPSHOT.jar>

See *Version History* below.


Installation
------------

1.  Setup Java libraries:

    1a) Put `couchdb-mailer-0.2-SNAPSHOT.jar` to a directory where you store java libraries  
    **IMPORTANT**: this directory must be readable by the user which runs the couchdb process (usually user `couchdb`).

    1b) Put the following 3rd party libraries to the same directory:
        mail-1.4.2.jar
        activation-1.1.1.jar
        couchdb4j-0.2.1-jdk14.jar *)
        json-lib-2.3-jdk13.jar
        commons-beanutils-core-1.8.0.jar
        commons-codec-1.4.jar
        commons-collections-3.2.1.jar
        commons-httpclient-3.1.jar
        commons-lang-2.4.jar
        commons-logging-1.1.1.jar
        ezmorph-1.0.6.jar

    *) CouchDB4J from jri's fork is required as it provides functionality required by CouchDB Mailer Extension.  
    <http://github.com/jri/couchdb4j-jdk14>

2.  Configure CouchDB: add lines to `/etc/couchdb/local.ini`

        [external]
        mailer=/usr/bin/java -server -Dmail.host=smtp.domain.com -jar /path/to/couchdb-mailer-0.2-SNAPSHOT.jar

        [httpd_db_handlers]
        _mailer = {couch_httpd_external, handle_external_req, <<"mailer">>}

    Replace `smtp.domain.com` with your SMTP server.
    This server is used by the CouchDB Mailer Extension to send your mails.

3.  Restart CouchDB


Usage
-----

### Send a mail ###

From your application send a POST request to

    http://www.your-couchdb-host.com/your-couchdb/_mailer

Put the mail content (recipients, subject ...) in the POST request's body, formatted as a JSON object.  
Example:

    {
        "sender": {"Karl Schlicht": "karl@example.de"},
        "recipients": {
            "to": {"Barbara Grünberg": "info@gruenberg.com"},
            "cc": {"Bertram Wooster": "berty@comedy.com", "Mehtap": "mehtap@domain.de"},
            "bcc": {}
        },
        "subject": "Test Mail",
        "message": "Message text.\n2nd line."
    }

Note: all fields are mandatory but field values may be empty (like "bcc" above).

### Attachments ###

Add the ID of a CouchDB document to the URL, e.g.:

    http://www.your-couchdb-host.com/your-couchdb/_mailer/0f5a40ff7f529fbf07083d31ac10dee3

All attachments of that CouchDB document will be added as attachments to the mail.


Version History
---------------

`v0.1` -- Oct 24, 2009 -- Basic functionality
`v0.2` -- Upcoming -- Attachments



------------
Jörg Richter  
Oct 25, 2009
