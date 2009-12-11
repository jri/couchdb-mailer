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
        Response response = null;
        while (true) {
            try {
                String line = reader.readLine();
                if (line == null) {
                    break;
                }
                response = processRequest(line);
            } catch (IOException e) {
                System.err.println("### error while reading request:");
                e.printStackTrace(System.err);
                response = new Response(false, "Error while reading request: " + e);
            } finally {
                sendResponse(response);
            }
        }
        System.err.println("### exiting couchdb-mailer extension");
    }

    /**
     * Parses the request and sends the mail.
     */
    private static Response processRequest(String line) {
        try {
            // --- parse request ---
            JSONObject request = JSONObject.fromObject(line);
            JSONObject body = JSONObject.fromObject(request.get("body"));
            JSONObject rcpts = JSONObject.fromObject(body.get("recipients"));
            initCouchDBSession(request);
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
            String format = body.getString("message-format");
            System.err.println("### content type: text/" + format);
            //
            List attachments = null;
            String docID = getDocID(request);
            if (docID != null) {
                attachments = getAttachments(getDatabase(request), docID);
            }
            // --- send mail ---
            return sendMail(sender, recipients, subject, message, format, attachments);
        } catch (Throwable e) {
            System.err.println("### error while processing request " + line);
            e.printStackTrace(System.err);
            return new Response(false, "ERROR: " + e);
        }
    }

    private static void sendResponse(Response response) {
        System.out.println("{\"json\": " + response.toJSON() + "}");
    }

    //

    private static Sender getSender(JSONObject sender) {
        try {
            String name = (String) sender.keys().next();
            return new Sender(sender.getString(name), name);
        } catch (Throwable e) {
            throw new RuntimeException("You entered no sender or sender is unknown", e);
        }
    }

    private static void addRecipients(JSONObject rcpts, List recipients, Message.RecipientType type) {
        Iterator i = rcpts.keys();
        while (i.hasNext()) {
            String rcpt = (String) i.next();
            recipients.add(new Recipient(rcpts.getString(rcpt), rcpt, type));
        }
    }

    private static List getAttachments(String database, String docID) {
        try {
            List attachments = new ArrayList();
            Database db = couchDBSession.getDatabase(database);
            Document doc = db.getDocument(docID);
            //
            JSONObject attachs = doc.getJSONObject("_attachments");
            System.err.println("### " + attachs.size() + " attachments");
            // Note: if the document has no _attachment field the JSON object is empty and keys() would throw a JSONException
            if (!attachs.isEmpty()) {
                Iterator i = attachs.keys();
                while (i.hasNext()) {
                    String fileName = (String) i.next();
                    JSONObject attachment = JSONObject.fromObject(attachs.get(fileName));
                    String mimeType = attachment.getString("content_type");
                    byte[] content = db.getAttachment(docID, fileName);
                    System.err.println("### " + fileName + " (" + mimeType + "), " + content.length + " bytes");
                    attachments.add(new Attachment(fileName, mimeType, content));
                }
            }
            return attachments;
        } catch (Throwable e) {
            throw new RuntimeException("Problem with attachment. Caused by: " + e, e);
        }
    }



    /**************************/
    /*** CouchDB Connection ***/
    /**************************/



    private static void initCouchDBSession(JSONObject request) {
        if (couchDBSession == null) {
            // figure out CouchDB port by examining the request
            JSONObject headers = JSONObject.fromObject(request.get("headers"));
            String[] hs = headers.getString("Host").split(":");
            String host = hs[0];
            int port = Integer.parseInt(hs[1]);
            System.err.println("### CouchDB host: " + host);
            System.err.println("### CouchDB port: " + port);
            //
            couchDBSession = new Session(host, port);
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



    public static Response sendMail(Sender sender, List recipients, String subject, String text, String format, List attachments) {
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
                message.setText(text, "UTF-8", format);
            } else {
                MimeMultipart multipart = createMultipart(text, format, attachments);
                message.setContent(multipart);
            }
            //
            Transport.send(message);
            return new Response(true, "Mail has been send to " + recipients.size() + " recipients.");
        } catch (Throwable e) {
            System.err.println("### error while sending mail \"" + subject + "\":");
            e.printStackTrace(System.err);
            return new Response(false, "ERROR: " + e);
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

    private static MimeMultipart createMultipart(String text, String format, List attachments) throws MessagingException {
        // build mutilpart
        MimeMultipart multipart = new MimeMultipart();
        // 1) add text part
		MimeBodyPart textPart = new MimeBodyPart();
		textPart.setText(text, "UTF-8", format);
		multipart.addBodyPart(textPart);
		// 2) add binary parts
		Iterator i = attachments.iterator();
		while (i.hasNext()) {
		    Attachment attachment = (Attachment) i.next();
		    //
    		MimeBodyPart binaryPart = new MimeBodyPart();
    		binaryPart.setContent(attachment.content, attachment.mimeType);
    		binaryPart.setFileName(attachment.fileName);
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

    private static class Response {

        boolean success;
        String message;

        Response(boolean success, String message) {
            this.success = success;
            this.message = message;
        }

        JSONObject toJSON() {
            JSONObject response = new JSONObject();
            response.element("success", success);
            response.element("message", message);
            return response;
        }
    }
}
