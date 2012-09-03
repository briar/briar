package net.sf.briar.plugins.email;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.Executor;
import javax.mail.Flags;
import javax.mail.Flags.Flag;
import javax.mail.Folder;
import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.NoSuchProviderException;
import javax.mail.PasswordAuthentication;
import javax.mail.Session;
import javax.mail.Store;
import javax.mail.Transport;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;
import javax.mail.search.FlagTerm;
import javax.microedition.io.StreamConnection;

import net.sf.briar.api.ContactId;
import net.sf.briar.api.TransportConfig;
import net.sf.briar.api.TransportProperties;
import net.sf.briar.api.crypto.PseudoRandom;
import net.sf.briar.api.plugins.simplex.SimplexPlugin;
import net.sf.briar.api.plugins.simplex.SimplexPluginCallback;
import net.sf.briar.api.plugins.simplex.SimplexTransportReader;
import net.sf.briar.api.plugins.simplex.SimplexTransportWriter;
import net.sf.briar.api.protocol.TransportId;
import net.sf.briar.util.StringUtils;

public class GmailPlugin implements SimplexPlugin {

	public static final byte[] TRANSPORT_ID = StringUtils
			.fromHexString("57ead1961d2120bbbbe8256ff9ce6ae2"
					+ "ef5535e44330c04cedcbafac4d756f0c"
					+ "e8dd928ed1d7a9e7b89fd62210aa30bf");

	private static final TransportId ID = new TransportId(TRANSPORT_ID);
	private final Executor pluginExecutor;
	private final SimplexPluginCallback callback;
	// private static GmailTransportConnectionReader reader;
	// private static GmailTransportConnectionWriter writer;

	public GmailPlugin(Executor pluginExecutor, SimplexPluginCallback callback) {
		this.pluginExecutor = pluginExecutor;
		this.callback = callback;
	}

	public TransportId getId() {
		return ID;
	}

	public void start() throws IOException {
		pluginExecutor.execute(new Runnable() {
			public void run() {
				connectIMAP();
			}
		});
	}

	protected void checkUnreadEmails(Folder inbox) {
		try {
			FlagTerm ft = new FlagTerm(new Flags(Flags.Flag.SEEN), false);
			Message msg[] = inbox.search(ft);
			// System.out.println("Unread Messages: "+ msg.length);
			for (final Message message : msg) {
				try {
					callback.readerCreated(new SimplexTransportReader() {

						public InputStream getInputStream() throws IOException {
							try {
								return message.getInputStream();
							} catch (MessagingException e) {
								e.printStackTrace();
							}
							return null;
						}

						public void dispose(boolean exception,
								boolean recognised) {
							try {
								message.setFlag(Flag.DELETED, recognised);
								message.setFlag(Flag.SEEN, recognised);
							} catch (MessagingException e) {
								e.printStackTrace();
							}
						}
					});

					// This part for testing purposes only
					System.out.println("DATE: "
							+ message.getSentDate().toString());
					System.out.println("FROM: "
							+ message.getFrom()[0].toString());
					System.out.println("SUBJECT: "
							+ message.getSubject().toString());
					System.out.println("CONTENT: "
							+ message.getContent().toString());
					System.out
							.println("=================================================");

				} catch (Exception e) {
					System.out.println("No Information");
				}
			}

		} catch (MessagingException e) {
			System.out.println(e.toString());
		}
	}

	protected void connectIMAP() {
		Properties props = System.getProperties();
		props.setProperty("mail.store.protocol", "imaps");
		final ArrayList<String> userPass = getAuthenticationDetails(callback
				.getConfig());
		if (userPass != null) {
			try {
				Session session = Session.getDefaultInstance(props, null);
				Store store = session.getStore("imaps");
				synchronized (this) {
					store.connect("imap.gmail.com", userPass.get(0),
							userPass.get(1));
				}

				Folder inbox = store.getFolder("Inbox");
				inbox.open(Folder.READ_ONLY);
				checkUnreadEmails(inbox);
			} catch (NoSuchProviderException e) {
				System.out.println(e.toString());
				System.exit(1);
			} catch (MessagingException e) {
				System.out.println(e.toString());
				System.exit(2);
			}
		}
	}

	protected boolean connectSMTP(ContactId cid) {
		boolean sent = false;
		if (discoverContactEmail(cid) != null) {
			Properties prop = new Properties();
			prop.put("mail.smtp.host", "smtp.gmail.com");
			prop.put("mail.smtp.socketFactory.port", "465");
			prop.put("mail.smtp.socketFactory.class",
					"javax.net.ssl.SSLSocketFactory");
			prop.put("mail.smtp.auth", "true");
			prop.put("mail.smtp.port", "465");

			final ArrayList<String> userPass = getAuthenticationDetails(callback
					.getConfig());

			if (userPass != null) {
				Session session;
				synchronized (this) {
					session = Session.getInstance(prop,
							new javax.mail.Authenticator() {
								protected PasswordAuthentication getPasswordAuthentication() {
									return new PasswordAuthentication(userPass
											.get(0), userPass.get(1));
								}
							});
				}
				// SimplexTransportWriter writer = createWriter(cid);
				sent = sendMessage(session, cid);
			}
		}
		return sent;
	}

	private synchronized boolean sendMessage(Session session, ContactId cid) {
		boolean sent = false;
		try {
			Message message = new MimeMessage(session);
			TransportProperties props = callback.getLocalProperties();
			String userEmail = props.get("email");

			message.setFrom(new InternetAddress(userEmail));
			message.setRecipients(Message.RecipientType.TO,
					InternetAddress.parse(discoverContactEmail(cid)));
			message.setSubject("Test Subject");
			message.setText("Test content");

			Transport.send(message);

			sent = true;
			return sent;
		} catch (MessagingException e) {
			return sent;
		}

	}

	public void stop() throws IOException {
		synchronized (this) {
			// close open connections
		}
	}

	public boolean shouldPoll() {
		return false;
	}

	public long getPollingInterval() throws UnsupportedOperationException {
		return 0;
	}

	public void poll(Collection<ContactId> connected)
			throws UnsupportedOperationException {
	}

	public boolean supportsInvitations() {
		return false;
	}

	/*
	 * Gets the user's authentication details ArrayList.get(0) = username,
	 * ArrayList.get(1) = password, or null if either value is null.
	 */
	private ArrayList<String> getAuthenticationDetails(TransportConfig config) {
		try {
			ArrayList<String> usernamePass = new ArrayList<String>();
			usernamePass.add(0, config.get("username"));
			usernamePass.add(1, config.get("password"));
			if (usernamePass.get(0) != null && usernamePass.get(1) != null)
				return usernamePass;
			else
				return null;
		} catch (Exception e) {
			return null;
		}
	}

	/*
	 * looks up the contact's email address given the contactID
	 * 
	 * @param ContactId
	 * 
	 * @return String email
	 */
	private String discoverContactEmail(ContactId cid) {
		try {
			Map<ContactId, TransportProperties> remote = callback
					.getRemoteProperties();
			TransportProperties tp = remote.get(cid);
			String address = tp.get("email");
			return address;
		} catch (Exception e) {
			return null;
		}
	}

	public SimplexTransportReader createReader(ContactId c) {
		return null;
	}

	public SimplexTransportWriter createWriter(ContactId c) {
		return null;
	}

	public SimplexTransportWriter sendInvitation(PseudoRandom r, long timeout)
			throws UnsupportedOperationException {
		return null;
	}

	public SimplexTransportReader acceptInvitation(PseudoRandom r, long timeout)
			throws UnsupportedOperationException {
		return null;
	}

	public SimplexTransportWriter sendInvitationResponse(PseudoRandom r,
			long timeout) throws UnsupportedOperationException {
		return null;
	}

	public SimplexTransportReader acceptInvitationResponse(PseudoRandom r,
			long timeout) throws UnsupportedOperationException {
		return null;
	}

}
