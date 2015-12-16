package org.briarproject.invitation;

import org.briarproject.api.Author;
import org.briarproject.api.AuthorFactory;
import org.briarproject.api.ContactId;
import org.briarproject.api.FormatException;
import org.briarproject.api.LocalAuthor;
import org.briarproject.api.TransportId;
import org.briarproject.api.TransportProperties;
import org.briarproject.api.crypto.CryptoComponent;
import org.briarproject.api.crypto.KeyPair;
import org.briarproject.api.crypto.KeyParser;
import org.briarproject.api.crypto.MessageDigest;
import org.briarproject.api.crypto.PseudoRandom;
import org.briarproject.api.crypto.SecretKey;
import org.briarproject.api.crypto.Signature;
import org.briarproject.api.data.Reader;
import org.briarproject.api.data.ReaderFactory;
import org.briarproject.api.data.Writer;
import org.briarproject.api.data.WriterFactory;
import org.briarproject.api.db.DatabaseComponent;
import org.briarproject.api.db.DbException;
import org.briarproject.api.plugins.ConnectionManager;
import org.briarproject.api.plugins.duplex.DuplexPlugin;
import org.briarproject.api.plugins.duplex.DuplexTransportConnection;
import org.briarproject.api.sync.Group;
import org.briarproject.api.sync.GroupFactory;
import org.briarproject.api.system.Clock;
import org.briarproject.api.transport.KeyManager;
import org.briarproject.api.transport.StreamReaderFactory;
import org.briarproject.api.transport.StreamWriterFactory;
import org.briarproject.api.transport.TransportKeys;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.logging.Logger;

import static java.util.logging.Level.INFO;
import static java.util.logging.Level.WARNING;
import static org.briarproject.api.AuthorConstants.MAX_AUTHOR_NAME_LENGTH;
import static org.briarproject.api.AuthorConstants.MAX_PUBLIC_KEY_LENGTH;
import static org.briarproject.api.AuthorConstants.MAX_SIGNATURE_LENGTH;
import static org.briarproject.api.TransportPropertyConstants.MAX_PROPERTIES_PER_TRANSPORT;
import static org.briarproject.api.TransportPropertyConstants.MAX_PROPERTY_LENGTH;
import static org.briarproject.api.TransportPropertyConstants.MAX_TRANSPORT_ID_LENGTH;
import static org.briarproject.api.invitation.InvitationConstants.CONNECTION_TIMEOUT;
import static org.briarproject.api.transport.TransportConstants.MAX_CLOCK_DIFFERENCE;

// FIXME: This class has way too many dependencies
abstract class Connector extends Thread {

	private static final Logger LOG =
			Logger.getLogger(Connector.class.getName());

	protected final CryptoComponent crypto;
	protected final DatabaseComponent db;
	protected final ReaderFactory readerFactory;
	protected final WriterFactory writerFactory;
	protected final StreamReaderFactory streamReaderFactory;
	protected final StreamWriterFactory streamWriterFactory;
	protected final AuthorFactory authorFactory;
	protected final GroupFactory groupFactory;
	protected final KeyManager keyManager;
	protected final ConnectionManager connectionManager;
	protected final Clock clock;
	protected final boolean reuseConnection;
	protected final ConnectorGroup group;
	protected final DuplexPlugin plugin;
	protected final LocalAuthor localAuthor;
	protected final Map<TransportId, TransportProperties> localProps;
	protected final PseudoRandom random;
	protected final String pluginName;

	private final KeyPair keyPair;
	private final KeyParser keyParser;
	private final MessageDigest messageDigest;

	private volatile ContactId contactId = null;

	Connector(CryptoComponent crypto, DatabaseComponent db,
			ReaderFactory readerFactory, WriterFactory writerFactory,
			StreamReaderFactory streamReaderFactory,
			StreamWriterFactory streamWriterFactory,
			AuthorFactory authorFactory, GroupFactory groupFactory,
			KeyManager keyManager, ConnectionManager connectionManager,
			Clock clock, boolean reuseConnection, ConnectorGroup group,
			DuplexPlugin plugin, LocalAuthor localAuthor,
			Map<TransportId, TransportProperties> localProps,
			PseudoRandom random) {
		super("Connector");
		this.crypto = crypto;
		this.db = db;
		this.readerFactory = readerFactory;
		this.writerFactory = writerFactory;
		this.streamReaderFactory = streamReaderFactory;
		this.streamWriterFactory = streamWriterFactory;
		this.authorFactory = authorFactory;
		this.groupFactory = groupFactory;
		this.keyManager = keyManager;
		this.connectionManager = connectionManager;
		this.clock = clock;
		this.reuseConnection = reuseConnection;
		this.group = group;
		this.plugin = plugin;
		this.localAuthor = localAuthor;
		this.localProps = localProps;
		this.random = random;
		pluginName = plugin.getClass().getName();
		keyPair = crypto.generateAgreementKeyPair();
		keyParser = crypto.getAgreementKeyParser();
		messageDigest = crypto.getMessageDigest();
	}

	protected DuplexTransportConnection createInvitationConnection() {
		if (LOG.isLoggable(INFO))
			LOG.info(pluginName + " creating invitation connection");
		return plugin.createInvitationConnection(random, CONNECTION_TIMEOUT);
	}

	protected void sendPublicKeyHash(Writer w) throws IOException {
		w.writeRaw(messageDigest.digest(keyPair.getPublic().getEncoded()));
		w.flush();
		if (LOG.isLoggable(INFO)) LOG.info(pluginName + " sent hash");
	}

	protected byte[] receivePublicKeyHash(Reader r) throws IOException {
		int hashLength = messageDigest.getDigestLength();
		byte[] b = r.readRaw(hashLength);
		if (b.length < hashLength) throw new FormatException();
		if (LOG.isLoggable(INFO)) LOG.info(pluginName + " received hash");
		return b;
	}

	protected void sendPublicKey(Writer w) throws IOException {
		byte[] key = keyPair.getPublic().getEncoded();
		w.writeRaw(key);
		w.flush();
		if (LOG.isLoggable(INFO)) LOG.info(pluginName + " sent key");
	}

	protected byte[] receivePublicKey(Reader r) throws GeneralSecurityException,
	IOException {
		byte[] b = r.readRaw(MAX_PUBLIC_KEY_LENGTH);
		keyParser.parsePublicKey(b);
		if (LOG.isLoggable(INFO)) LOG.info(pluginName + " received key");
		return b;
	}

	protected SecretKey deriveMasterSecret(byte[] hash, byte[] key,
			boolean alice) throws GeneralSecurityException {
		// Check that the hash matches the key
		if (!Arrays.equals(hash, messageDigest.digest(key))) {
			if (LOG.isLoggable(INFO))
				LOG.info(pluginName + " hash does not match key");
			throw new GeneralSecurityException();
		}
		//  Derive the master secret
		if (LOG.isLoggable(INFO))
			LOG.info(pluginName + " deriving master secret");
		return crypto.deriveMasterSecret(key, keyPair, alice);
	}

	protected void sendConfirmation(Writer w, boolean confirmed)
			throws IOException {
		w.writeBoolean(confirmed);
		w.flush();
		if (LOG.isLoggable(INFO))
			LOG.info(pluginName + " sent confirmation: " + confirmed);
	}

	protected boolean receiveConfirmation(Reader r) throws IOException {
		boolean confirmed = r.readBoolean();
		if (LOG.isLoggable(INFO))
			LOG.info(pluginName + " received confirmation: " + confirmed);
		return confirmed;
	}

	protected void sendPseudonym(Writer w, byte[] nonce)
			throws GeneralSecurityException, IOException {
		// Sign the nonce
		Signature signature = crypto.getSignature();
		KeyParser keyParser = crypto.getSignatureKeyParser();
		byte[] privateKey = localAuthor.getPrivateKey();
		signature.initSign(keyParser.parsePrivateKey(privateKey));
		signature.update(nonce);
		byte[] sig = signature.sign();
		// Write the name, public key and signature
		w.writeString(localAuthor.getName());
		w.writeRaw(localAuthor.getPublicKey());
		w.writeRaw(sig);
		w.flush();
		if (LOG.isLoggable(INFO)) LOG.info(pluginName + " sent pseudonym");
	}

	protected Author receivePseudonym(Reader r, byte[] nonce)
			throws GeneralSecurityException, IOException {
		// Read the name, public key and signature
		String name = r.readString(MAX_AUTHOR_NAME_LENGTH);
		byte[] publicKey = r.readRaw(MAX_PUBLIC_KEY_LENGTH);
		byte[] sig = r.readRaw(MAX_SIGNATURE_LENGTH);
		if (LOG.isLoggable(INFO)) LOG.info(pluginName + " received pseudonym");
		// Verify the signature
		Signature signature = crypto.getSignature();
		KeyParser keyParser = crypto.getSignatureKeyParser();
		signature.initVerify(keyParser.parsePublicKey(publicKey));
		signature.update(nonce);
		if (!signature.verify(sig)) {
			if (LOG.isLoggable(INFO))
				LOG.info(pluginName + " invalid signature");
			throw new GeneralSecurityException();
		}
		return authorFactory.createAuthor(name, publicKey);
	}

	protected void sendTimestamp(Writer w, long timestamp) throws IOException {
		w.writeInteger(timestamp);
		w.flush();
		if (LOG.isLoggable(INFO)) LOG.info(pluginName + " sent timestamp");
	}

	protected long receiveTimestamp(Reader r) throws IOException {
		long timestamp = r.readInteger();
		if (timestamp < 0) throw new FormatException();
		if (LOG.isLoggable(INFO)) LOG.info(pluginName + " received timestamp");
		return timestamp;
	}

	protected void sendTransportProperties(Writer w) throws IOException {
		w.writeListStart();
		for (Entry<TransportId, TransportProperties> e : localProps.entrySet()) {
			w.writeString(e.getKey().getString());
			w.writeMap(e.getValue());
		}
		w.writeListEnd();
		w.flush();
		if (LOG.isLoggable(INFO))
			LOG.info(pluginName + " sent transport properties");
	}

	protected Map<TransportId, TransportProperties> receiveTransportProperties(
			Reader r) throws IOException {
		Map<TransportId, TransportProperties> remoteProps =
				new HashMap<TransportId, TransportProperties>();
		r.readListStart();
		while (!r.hasListEnd()) {
			String idString = r.readString(MAX_TRANSPORT_ID_LENGTH);
			if (idString.length() == 0) throw new FormatException();
			TransportId id = new TransportId(idString);
			Map<String, String> p = new HashMap<String, String>();
			r.readMapStart();
			for (int i = 0; !r.hasMapEnd(); i++) {
				if (i == MAX_PROPERTIES_PER_TRANSPORT)
					throw new FormatException();
				String key = r.readString(MAX_PROPERTY_LENGTH);
				String value = r.readString(MAX_PROPERTY_LENGTH);
				p.put(key, value);
			}
			r.readMapEnd();
			remoteProps.put(id, new TransportProperties(p));
		}
		r.readListEnd();
		if (LOG.isLoggable(INFO))
			LOG.info(pluginName + " received transport properties");
		return remoteProps;
	}

	protected void addContact(Author remoteAuthor,
			Map<TransportId, TransportProperties> remoteProps, SecretKey master,
			long timestamp, boolean alice) throws DbException {
		// Add the contact to the database
		contactId = db.addContact(remoteAuthor, localAuthor.getId());
		// Create and store the inbox group
		byte[] salt = crypto.deriveGroupSalt(master);
		Group inbox = groupFactory.createGroup("Inbox", salt);
		db.addGroup(inbox);
		db.setInboxGroup(contactId, inbox);
		// Store the remote transport properties
		db.setRemoteProperties(contactId, remoteProps);
		// Derive transport keys for each transport shared with the contact
		Map<TransportId, Integer> latencies = db.getTransportLatencies();
		List<TransportKeys> keys = new ArrayList<TransportKeys>();
		for (TransportId t : localProps.keySet()) {
			if (remoteProps.containsKey(t) && latencies.containsKey(t)) {
				// Work out what rotation period the timestamp belongs to
				long latency = latencies.get(t);
				long rotationPeriodLength = latency + MAX_CLOCK_DIFFERENCE;
				long rotationPeriod = timestamp / rotationPeriodLength;
				// Derive the transport keys
				TransportKeys k = crypto.deriveTransportKeys(t, master,
						rotationPeriod, alice);
				db.addTransportKeys(contactId, k);
				keys.add(k);
			}
		}
		keyManager.contactAdded(contactId, keys);
	}

	protected void tryToClose(DuplexTransportConnection conn,
			boolean exception) {
		try {
			LOG.info("Closing connection");
			conn.getReader().dispose(exception, true);
			conn.getWriter().dispose(exception);
		} catch (IOException e) {
			if (LOG.isLoggable(WARNING)) LOG.log(WARNING, e.toString(), e);
		}
	}

	protected void reuseConnection(DuplexTransportConnection conn) {
		if (contactId == null) throw new IllegalStateException();
		TransportId t = plugin.getId();
		connectionManager.manageOutgoingConnection(contactId, t, conn);
	}
}
