package net.sf.briar.plugins.email;

import static java.util.logging.Level.WARNING;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.Executor;
import java.util.logging.Logger;

import javax.activation.DataHandler;
import javax.activation.DataSource;
import javax.mail.Authenticator;
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
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimeMultipart;
import javax.mail.search.FlagTerm;
import javax.mail.util.ByteArrayDataSource;

import net.sf.briar.api.ContactId;
import net.sf.briar.api.TransportConfig;
import net.sf.briar.api.TransportProperties;
import net.sf.briar.api.plugins.simplex.SimplexPlugin;
import net.sf.briar.api.plugins.simplex.SimplexPluginCallback;
import net.sf.briar.api.plugins.simplex.SimplexTransportReader;
import net.sf.briar.api.plugins.simplex.SimplexTransportWriter;
import net.sf.briar.api.protocol.TransportId;
import net.sf.briar.util.StringUtils;

class GmailPlugin implements SimplexPlugin {

	private static final byte[] TRANSPORT_ID = StringUtils
			.fromHexString("57ead1961d2120bbbbe8256ff9ce6ae2"
					+ "ef5535e44330c04cedcbafac4d756f0c"
					+ "e8dd928ed1d7a9e7b89fd62210aa30bf");
	private static final TransportId ID = new TransportId(TRANSPORT_ID);
	private static final Logger LOG =
			Logger.getLogger(GmailPlugin.class.getName());

	private final Executor pluginExecutor;
	private final SimplexPluginCallback callback;

	public GmailPlugin(Executor pluginExecutor, SimplexPluginCallback callback) {
		this.pluginExecutor = pluginExecutor;
		this.callback = callback;
	}

	public TransportId getId() {
		return ID;
	}

	public String getName() {
		return "GMAIL_PLUGIN_NAME";
	}

	public void start() throws IOException {
		pluginExecutor.execute(new Runnable() {
			public void run() {
				connectIMAP();
			}
		});
	}

	private void checkUnreadEmails(Folder inbox) {
		try {
			FlagTerm ft = new FlagTerm(new Flags(Flags.Flag.SEEN), false);
			Message msg[] = inbox.search(ft);
			for(final Message message : msg) {
				callback.readerCreated(new SimplexTransportReader() {

					public InputStream getInputStream() throws IOException {
						try {
							return message.getInputStream();
						} catch(MessagingException e) {
							if(LOG.isLoggable(WARNING))
								LOG.warning(e.toString());
						}
						return null;
					}

					public void dispose(boolean exception, boolean recognised)
							throws IOException {
						try {
							message.setFlag(Flag.DELETED, recognised);
							message.setFlag(Flag.SEEN, recognised);
						} catch(MessagingException e) {
							if(LOG.isLoggable(WARNING))
								LOG.warning(e.toString());
						}
					}
				});
			}
		} catch(MessagingException e) {
			if(LOG.isLoggable(WARNING)) LOG.warning(e.toString());
		}
	}

	private void connectIMAP() {
		Properties props = new Properties();
		props.setProperty("mail.store.protocol", "imaps");
		final ArrayList<String> userPass = getAuthenticationDetails(callback
				.getConfig());
		if(userPass != null) {
			try {
				Session session = Session.getInstance(props, null);
				Store store = session.getStore("imaps");
				store.connect("imap.gmail.com", userPass.get(0),
						userPass.get(1));
				Folder inbox = store.getFolder("Inbox");
				inbox.open(Folder.READ_ONLY);
				checkUnreadEmails(inbox);
			} catch(NoSuchProviderException e) {
				if(LOG.isLoggable(WARNING)) LOG.warning(e.toString());
			} catch(MessagingException e) {
				if(LOG.isLoggable(WARNING)) LOG.warning(e.toString());
			}
		}
	}

	public boolean connectSMTP(ContactId cid) {
		boolean sent = false;
		if(discoverContactEmail(cid) != null) {
			Properties props = new Properties();
			props.put("mail.smtp.host", "smtp.gmail.com");
			props.put("mail.smtp.socketFactory.port", "465");
			props.put("mail.smtp.socketFactory.class",
					"javax.net.ssl.SSLSocketFactory");
			props.put("mail.smtp.auth", "true");
			props.put("mail.smtp.port", "465");

			final ArrayList<String> userPass =
					getAuthenticationDetails(callback.getConfig());

			if(userPass != null) {
				Session session;
				session = Session.getInstance(props,
						new Authenticator() {
					protected PasswordAuthentication getPasswordAuthentication() {
						return new PasswordAuthentication(
								userPass.get(0), userPass.get(1));
					}
				});
				sent = sendMessage(session, cid);
			}
		}
		return sent;
	}

	private boolean sendMessage(Session session, ContactId cid) {
		ByteArrayOutputStream outputStream = null;
		try {
			Message message = new MimeMessage(session);
			TransportProperties props = callback.getLocalProperties();
			String userEmail = props.get("email");

			message.setFrom(new InternetAddress(userEmail));
			message.setRecipients(Message.RecipientType.TO,
					InternetAddress.parse(discoverContactEmail(cid)));
			message.setSubject("Test Subject");

			outputStream = new ByteArrayOutputStream();

			callback.writerCreated(cid, new SimplexTransportWriter() {

				public boolean shouldFlush() {
					return false;
				}

				public OutputStream getOutputStream() throws IOException {
					return null; // FIXME
				}

				public long getCapacity() {
					return 0; // FIXME
				}

				public void dispose(boolean exception) throws IOException {
					// FIXME
				}
			});

			byte[] bytes = outputStream.toByteArray();
			DataSource dataSource = new ByteArrayDataSource(bytes,
					"application/octet-stream");

			MimeBodyPart messageBodyPart = new MimeBodyPart();
			messageBodyPart.setDataHandler(new DataHandler(dataSource));

			MimeMultipart mimeMultipart = new MimeMultipart();
			mimeMultipart.addBodyPart(messageBodyPart);

			message.setContent(mimeMultipart);

			Transport.send(message);

			return true;
		} catch(MessagingException e) {
			return false;
		} finally {
			if(outputStream != null) {
				try {
					outputStream.close();
					outputStream = null;
				} catch(Exception e) {
					if(LOG.isLoggable(WARNING)) LOG.warning(e.toString());
				}
			}
		}

	}

	public void stop() throws IOException {
		synchronized(this) {
			// FIXME: Close open connections
		}
	}

	public boolean shouldPoll() {
		return false;
	}

	public long getPollingInterval() {
		throw new UnsupportedOperationException();
	}

	public void poll(Collection<ContactId> connected) {
		throw new UnsupportedOperationException();
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
			if(usernamePass.get(0) != null && usernamePass.get(1) != null) {
				return usernamePass;
			} else {
				return null;
			}
		} catch(Exception e) {
			return null;
		}
	}

	/*
	 * Looks up the contact's email address given the contactID
	 * 
	 * @param ContactId
	 * 
	 * @return String email
	 */
	private String discoverContactEmail(ContactId cid) {
		try {
			Map<ContactId, TransportProperties> remote =
					callback.getRemoteProperties();
			TransportProperties tp = remote.get(cid);
			if(tp != null) {
				String address = tp.get("email");
				return address;
			} else {
				return null;
			}
		} catch(Exception e) {
			return null;
		}
	}

	public SimplexTransportReader createReader(ContactId c) {
		return null;
	}

	public SimplexTransportWriter createWriter(ContactId c) {
		return null;
	}
}
