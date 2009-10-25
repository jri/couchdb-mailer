package de.deepamehta.couchdb.mailer;

import javax.mail.Address;
import javax.mail.Message;
import javax.mail.MessagingException;
// import javax.mail.Session;   // name clash with CouchDB session
import javax.mail.Transport;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimeMultipart;

import net.sf.json.JSON;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;
import net.sf.json.JSONSerializer;

import com.fourspaces.couchdb.Database;
import com.fourspaces.couchdb.Document;
import com.fourspaces.couchdb.Session;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Properties;



public final class Main {

    private static final String TEXT_NO_SUBJECT = "<No Subject>";

    private static javax.mail.Session mailSession;
    private static Session couchDBSession;

    public static void main(final String[] args) {
        System.err.println("### initializing couchdb-mailer extension");
        initMailSession();
        BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
        while (true) {
            try {
                String line = reader.readLine();
                if (line == null) {
                    break;
                }
                processRequest(line);
                sendResponse();
            } catch (IOException e) {
                throw new RuntimeException("### error while reading request", e);
            }
        }
        System.err.println("### exiting couchdb-mailer extension");
    }

    private static void processRequest(String line) {
        try {
            JSONObject request = JSONObject.fromObject(line);
            JSONObject body = JSONObject.fromObject(request.get("body"));
            JSONObject rcpts = JSONObject.fromObject(body.get("recipients"));
            initCouchDBSession(request);
            // System.err.println("### request body=" + body);
            //
            Sender sender = getSender(JSONObject.fromObject(body.get("sender")));
            //
            List recipients = new ArrayList();
            addRecipients(JSONObject.fromObject(rcpts.get("to")), recipients, Message.RecipientType.TO);
            addRecipients(JSONObject.fromObject(rcpts.get("cc")), recipients, Message.RecipientType.CC);
            addRecipients(JSONObject.fromObject(rcpts.get("bcc")), recipients, Message.RecipientType.BCC);
            //
            String subject = body.getString("subject");
            String message = body.getString("message");
            //
            List attachments = null;
            String docID = getDocID(request);
            if (docID != null) {
                attachments = getAttachments(getDatabase(request), docID);
            }
            //
            sendMail(sender, recipients, subject, message, attachments);
        } catch (Throwable e) {
            System.err.println("### error while processing request: " + e);
            e.printStackTrace(System.err);
            System.err.println("### request: " + line);
        }
    }

    private static void sendResponse() {
        System.out.println("{\"body\": \"mail sent sucessfully\"}");
    }

    //

    private static Sender getSender(JSONObject sender) {
        String name = (String) sender.keys().next();
        return new Sender(sender.getString(name), name);
    }

    private static void addRecipients(JSONObject rcpts, List recipients, Message.RecipientType type) {
        Iterator i = rcpts.keys();
        while (i.hasNext()) {
            String rcpt = (String) i.next();
            recipients.add(new Recipient(rcpts.getString(rcpt), rcpt, type));
        }
    }

    private static List getAttachments(String database, String docID) {
        List attachments = new ArrayList();
        try {
            Database db = couchDBSession.getDatabase(database);
            Document doc = db.getDocument(docID);
            //
            JSONObject attachs = doc.getJSONObject("_attachments");
            System.err.println("### " + attachs.size() + " attachments");
            Iterator i = attachs.keys();
            while (i.hasNext()) {
                String fileName = (String) i.next();
                JSONObject attachment = JSONObject.fromObject(attachs.get(fileName));
                String mimeType = attachment.getString("content_type");
                byte[] content = db.getAttachment(docID, fileName);
                System.err.println("### " + fileName + " (" + mimeType + "), " + content.length + " bytes");
                attachments.add(new Attachment(fileName, mimeType, content));
            }
        } catch (Throwable e) {
            System.err.println("### error while retrieving attachments: " + e);
        }
        return attachments;
    }



    /**************************/
    /*** CouchDB Connection ***/
    /**************************/



    private static void initCouchDBSession(JSONObject request) {
        if (couchDBSession == null) {
            // figure out CouchDB port by examining the request
            JSONObject headers = JSONObject.fromObject(request.get("headers"));
            String host = headers.getString("Host");
            int port = Integer.parseInt(host.split(":")[1]);
            System.err.println("### CouchDB port: " + port);
            //
            couchDBSession = new Session("localhost", port);     // ### TODO: configurable host
        }
    }

    private static String getDatabase(JSONObject request) {
        JSONArray path = JSONArray.fromObject(request.get("path"));
        String database = path.getString(0);
        System.err.println("### database: " + database);
        return database;
    }

    /**
     * @return  doc ID or null
     */
    private static String getDocID(JSONObject request) {
        JSONArray path = JSONArray.fromObject(request.get("path"));
        String docID = path.size() > 2 ? path.getString(2) : null;
        System.err.println("### docID: " + docID);
        return docID;
    }



    /*********************/
    /*** JavaMail Part ***/
    /*********************/



    public static void sendMail(Sender sender, List recipients, String subject, String text, List attachments) {
        try {
            MimeMessage message = new MimeMessage(mailSession);
            // sender
            message.setFrom(new InternetAddress(sender.address, sender.personal, "UTF-8"));
            // recipients
            Iterator i = recipients.iterator();
            while (i.hasNext()) {
                Recipient recipient = (Recipient) i.next();
                Address address = new InternetAddress(recipient.address, recipient.personal, "UTF-8");
                message.addRecipient(recipient.type, address);
            }
            // subject, date
            if (subject == null || subject.equals("")) {
                subject = TEXT_NO_SUBJECT;
            }
            message.setSubject(subject, "UTF-8");
            message.setSentDate(new Date());
            // content
            if (attachments.size() == 0) {
                message.setText(text, "UTF-8");
            } else {
                MimeMultipart multipart = createMultipart(text, attachments);
                message.setContent(multipart);
            }
            //
            Transport.send(message);
        } catch (Throwable e) {
            System.err.println("### error while sending mail: " + e);
        }
    }

    private static void initMailSession() {
        String host = System.getProperty("mail.host", "localhost");
        System.err.println("### mail host: " + host);
        //
        Properties props = new Properties();
        props.put("mail.host", host);
        //
        mailSession = javax.mail.Session.getDefaultInstance(props);
        mailSession.setDebugOut(System.err);    // Note: must redirect before debug is switched on
        mailSession.setDebug(true);
    }

    private static MimeMultipart createMultipart(String text, List attachments) throws MessagingException {
        // build mutilpart
        MimeMultipart multipart = new MimeMultipart();
        // 1) add text part
		MimeBodyPart textPart = new MimeBodyPart();
		textPart.setText(text, "UTF-8");
		multipart.addBodyPart(textPart);
		// 2) add binary parts
		Iterator i = attachments.iterator();
		while (i.hasNext()) {
		    Attachment attachment = (Attachment) i.next();
		    //
    		MimeBodyPart binaryPart = new MimeBodyPart();
    		binaryPart.setContent(attachment.content, attachment.mimeType);
    		multipart.addBodyPart(binaryPart);
	    }
	    //
	    return multipart;
    }



    /**********************/
    /*** Helper Classes ***/
    /**********************/



    private static class Sender {

        String address;
        String personal;

        Sender(String address, String personal) {
            this.address = address;
            this.personal = personal;
        }
    }

    private static class Recipient {

        String address;
        String personal;
        Message.RecipientType type;

        Recipient(String address, String personal, Message.RecipientType type) {
            this.address = address;
            this.personal = personal;
            this.type = type;
        }

        // FIXME: not yet used
        public boolean equals(Object o) {
            Recipient r = (Recipient) o;
            return r.type == type && r.address.equals(address);
        }
    }

    private static class Attachment {
        String fileName;
        String mimeType;
        byte[] content;

        Attachment(String fileName, String mimeType, byte[] content) {
            this.fileName = fileName;
            this.mimeType = mimeType;
            this.content = content;
        }
    }
}
