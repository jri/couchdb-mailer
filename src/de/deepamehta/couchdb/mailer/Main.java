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

    private static BufferedReader reader;

    public static void main(final String[] args) {

        System.err.println("### initializing couchdb-mailer extension");
        reader = new BufferedReader(new InputStreamReader(System.in));

        while (true) {
            try {
                String line = reader.readLine();
                if (line == null) {
                    break;
                }
                // System.err.println("### got line: " + line);
                process(line);
                respond();
            } catch (IOException e) {
                throw new RuntimeException("### can't read line", e);
            }
        }
        System.err.println("### exiting couchdb-mailer");
    }

    private static void process(String line) {
        JSONObject request = JSONObject.fromObject(line);
        JSONObject rcpts = JSONObject.fromObject(request.get("body"));
        List recipients = new ArrayList();
        Iterator i = rcpts.keys();
        while (i.hasNext()) {
            String rcpt = (String) i.next();
            recipients.add(new Recipient(rcpts.getString(rcpt), rcpt, Message.RecipientType.TO));
        }
        //
        sendMail("jri@deepamehta.de", recipients, "Test", "Text\n2ndline");
    }

    private static void respond() {
        System.out.println("{\"body\": \"mail sent sucessfully\"}");
    }

    /*** JavaMail Part ***/

    public static void sendMail(String from, List recipients, String subject, String body) {
        try {
            MimeMessage msg = new MimeMessage(getMailSession());
            msg.setFrom(new InternetAddress(from));
            //
            Iterator i = recipients.iterator();
            while (i.hasNext()) {
                Recipient recipient = (Recipient) i.next();
                Address address = new InternetAddress(recipient.address, recipient.personal);
                msg.addRecipient(recipient.type, address);
            }
            //
            if (subject == null || subject.equals("")) {
                subject = TEXT_NO_SUBJECT;
            }
            msg.setSubject(subject);
            msg.setText(body, "UTF-8");
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
        Session session = Session.getDefaultInstance(props);    // ### authenticator=null
        session.setDebugOut(System.err);    // must redirect before debug is switched on
        session.setDebug(true);                                 // ###
        return session;
    }

    // Inner Class

    private static class Recipient {

        String address;
        String personal;
        Message.RecipientType type;

        Recipient(String address, String personal, Message.RecipientType type) {
            this.address = address;
            this.personal = personal;
            this.type = type;
        }

        public boolean equals(Object o) {
            Recipient r = (Recipient) o;
            return r.type == type && r.address.equals(address);
        }
    }
}
