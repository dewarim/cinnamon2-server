package server;

import java.util.Date;
import java.util.Properties;

import javax.mail.Message;
import javax.mail.Session;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;
import com.sun.mail.smtp.SMTPTransport;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import server.global.ConfThreadLocal;

/**
 * Simple class to send mail from Cinnamon. Uses javamail library. To use this class,
 * you need to add a mail configuration to your cinnamon_config and replace the default
 * settings with the data of an existing mail server account.
 * <pre>
 * {@code 
 *	<mail>
 *		<smtp-host>example.invalid</smtp-host>
 *		<user>cinnamon</user>
 *		<password>_cinnamon's_password</password>
 *	</mail> 
 * }
 * </pre>
 *
 */
public class MailSender {
	
	private transient Logger log = LoggerFactory.getLogger(this.getClass());
	
	private Exception fail = null;
	
	/**
	 * Send a simple text mail to a recipient. If this fails, the resulting exception
	 * is stored in {@link server.MailSender#fail MailSender.fail}
	 * @param from the sender of this mail
	 * @param to the recipient of the mail
	 * @param subject the mail subject line
	 * @param body the content of the mail
	 * @return <code>true</code> if the mail was successfully sent, <code>false</code> otherwise.
	 */
	public Boolean sendMail(String from, String to, String subject, String body){
		ConfThreadLocal conf = ConfThreadLocal.getConf();
		Properties props = new Properties();
		props.put("mail.smtp.host", conf.getField("mail/smtp-host", "example.invalid"));
		props.put("mail.user", conf.getField("mail/user", ""));
		props.put("mail.password", conf.getField("mail/password", ""));
		
		Session session = Session.getInstance(props);
		Boolean sentMsg = true;
		try {
		    MimeMessage msg = new MimeMessage(session);	    
		    msg.setFrom(new InternetAddress(from));
		    InternetAddress[] address = {new InternetAddress(to)};
		    msg.setRecipients(Message.RecipientType.TO, address);
		    msg.setSubject(subject);
		    
		    msg.setText(body);

		    // set the Date: header
		    msg.setSentDate(new Date());
		    
		    SMTPTransport transport = (SMTPTransport) session.getTransport("smtp");
			try {
				transport.connect(
					props.getProperty("mail.smtp.host"), 
					props.getProperty("mail.user"), 
					props.getProperty("mail.password")
				);
				transport.sendMessage(msg, msg.getAllRecipients());
			} finally {
				transport.close();
			}
		    
		} catch (Exception ex) {
			sentMsg = false;
			log.warn("sendMail failed:\n",ex);
			fail = ex;
		}
		return sentMsg;
	}
	
	/**
	 * If sending a message has failed, you can get the last exception thrown 
	 * by calling this method. 
	 * @return the fail
	 */
	public Exception getFail() {
		return fail;
	}

	/**
	 * Test code
	 * @param args command line arguments
	 */
	public static void main(String[] args){
		MailSender sender = new MailSender();
		String admin = ConfThreadLocal.getConf().getField("system-administrator", "admin@example.invalid");
		sender.sendMail(admin, admin, "MailSender-Test", "Test data. With Ümlauts.");
	}
}
