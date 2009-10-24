package de.deepamehta.couchdb.mailer;

import javax.mail.Address;
import javax.mail.Message;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;

import net.sf.json.JSON;
import net.sf.json.JSONObject;
import net.sf.json.JSONSerializer;

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

    public static void main(final String[] args) {
        System.err.println("### initializing couchdb-mailer extension");
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
            sendMail(sender, recipients, subject, message);
        } catch (Throwable e) {
            System.err.println("### error while processing request: " + e);
            System.err.println("### request=" + line);
        }
    }

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

    private static void sendResponse() {
        System.out.println("{\"body\": \"mail sent sucessfully\"}");
    }

    /*** JavaMail Part ***/

    public static void sendMail(Sender sender, List recipients, String subject, String text) {
        try {
            MimeMessage msg = new MimeMessage(getMailSession());
            // set "from"
            msg.setFrom(new InternetAddress(sender.address, sender.personal, "UTF-8"));
            // add recipients
            Iterator i = recipients.iterator();
            while (i.hasNext()) {
                Recipient recipient = (Recipient) i.next();
                Address address = new InternetAddress(recipient.address, recipient.personal, "UTF-8");
                msg.addRecipient(recipient.type, address);
            }
            // set subject, text, and date
            if (subject == null || subject.equals("")) {
                subject = TEXT_NO_SUBJECT;
            }
            msg.setSubject(subject, "UTF-8");
            msg.setText(text, "UTF-8");
            msg.setSentDate(new Date());
            //
            Transport.send(msg);
        } catch (Throwable e) {
            System.err.println("### error while sending mail: " + e);
        }
    }

    private static Session getMailSession() {
        Properties props = new Properties();
        String host = System.getProperty("mail.host", "localhost");
        System.err.println("### mail host: " + host);
        props.put("mail.host", host);
        Session session = Session.getDefaultInstance(props);
        session.setDebugOut(System.err);    // Note: must redirect before debug is switched on
        session.setDebug(true);
        return session;
    }

    /*** Helper Classes ***/

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
}
