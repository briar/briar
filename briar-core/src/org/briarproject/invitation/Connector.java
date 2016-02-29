package org.briarproject.invitation;

import org.briarproject.api.FormatException;
import org.briarproject.api.TransportId;
import org.briarproject.api.contact.ContactId;
import org.briarproject.api.contact.ContactManager;
import org.briarproject.api.crypto.CryptoComponent;
import org.briarproject.api.crypto.KeyPair;
import org.briarproject.api.crypto.KeyParser;
import org.briarproject.api.crypto.MessageDigest;
import org.briarproject.api.crypto.PseudoRandom;
import org.briarproject.api.crypto.SecretKey;
import org.briarproject.api.crypto.Signature;
import org.briarproject.api.data.BdfReader;
import org.briarproject.api.data.BdfReaderFactory;
import org.briarproject.api.data.BdfWriter;
import org.briarproject.api.data.BdfWriterFactory;
import org.briarproject.api.db.DbException;
import org.briarproject.api.identity.Author;
import org.briarproject.api.identity.AuthorFactory;
import org.briarproject.api.identity.LocalAuthor;
import org.briarproject.api.plugins.ConnectionManager;
import org.briarproject.api.plugins.duplex.DuplexPlugin;
import org.briarproject.api.plugins.duplex.DuplexTransportConnection;
import org.briarproject.api.system.Clock;
import org.briarproject.api.transport.StreamReaderFactory;
import org.briarproject.api.transport.StreamWriterFactory;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.Arrays;
import java.util.logging.Logger;

import static java.util.logging.Level.INFO;
import static java.util.logging.Level.WARNING;
import static org.briarproject.api.identity.AuthorConstants.MAX_AUTHOR_NAME_LENGTH;
import static org.briarproject.api.identity.AuthorConstants.MAX_PUBLIC_KEY_LENGTH;
import static org.briarproject.api.identity.AuthorConstants.MAX_SIGNATURE_LENGTH;
import static org.briarproject.api.invitation.InvitationConstants.CONNECTION_TIMEOUT;

// FIXME: This class has way too many dependencies
abstract class Connector extends Thread {

	private static final Logger LOG =
			Logger.getLogger(Connector.class.getName());

	protected final CryptoComponent crypto;
	protected final BdfReaderFactory bdfReaderFactory;
	protected final BdfWriterFactory bdfWriterFactory;
	protected final StreamReaderFactory streamReaderFactory;
	protected final StreamWriterFactory streamWriterFactory;
	protected final AuthorFactory authorFactory;
	protected final ConnectionManager connectionManager;
	protected final ContactManager contactManager;
	protected final Clock clock;
	protected final ConnectorGroup group;
	protected final DuplexPlugin plugin;
	protected final LocalAuthor localAuthor;
	protected final PseudoRandom random;
	protected final String pluginName;

	private final KeyPair keyPair;
	private final KeyParser keyParser;
	private final MessageDigest messageDigest;

	private volatile ContactId contactId = null;

	Connector(CryptoComponent crypto,
			BdfReaderFactory bdfReaderFactory,
			BdfWriterFactory bdfWriterFactory,
			StreamReaderFactory streamReaderFactory,
			StreamWriterFactory streamWriterFactory,
			AuthorFactory authorFactory, ConnectionManager connectionManager,
			ContactManager contactManager, Clock clock, ConnectorGroup group,
			DuplexPlugin plugin, LocalAuthor localAuthor, PseudoRandom random) {
		super("Connector");
		this.crypto = crypto;
		this.bdfReaderFactory = bdfReaderFactory;
		this.bdfWriterFactory = bdfWriterFactory;
		this.streamReaderFactory = streamReaderFactory;
		this.streamWriterFactory = streamWriterFactory;
		this.authorFactory = authorFactory;
		this.connectionManager = connectionManager;
		this.contactManager = contactManager;
		this.clock = clock;
		this.group = group;
		this.plugin = plugin;
		this.localAuthor = localAuthor;
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

	protected void sendPublicKeyHash(BdfWriter w) throws IOException {
		w.writeRaw(messageDigest.digest(keyPair.getPublic().getEncoded()));
		w.flush();
		if (LOG.isLoggable(INFO)) LOG.info(pluginName + " sent hash");
	}

	protected byte[] receivePublicKeyHash(BdfReader r) throws IOException {
		int hashLength = messageDigest.getDigestLength();
		byte[] b = r.readRaw(hashLength);
		if (b.length < hashLength) throw new FormatException();
		if (LOG.isLoggable(INFO)) LOG.info(pluginName + " received hash");
		return b;
	}

	protected void sendPublicKey(BdfWriter w) throws IOException {
		byte[] key = keyPair.getPublic().getEncoded();
		w.writeRaw(key);
		w.flush();
		if (LOG.isLoggable(INFO)) LOG.info(pluginName + " sent key");
	}

	protected byte[] receivePublicKey(BdfReader r)
			throws GeneralSecurityException, IOException {
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

	protected void sendConfirmation(BdfWriter w, boolean confirmed)
			throws IOException {
		w.writeBoolean(confirmed);
		w.flush();
		if (LOG.isLoggable(INFO))
			LOG.info(pluginName + " sent confirmation: " + confirmed);
	}

	protected boolean receiveConfirmation(BdfReader r) throws IOException {
		boolean confirmed = r.readBoolean();
		if (LOG.isLoggable(INFO))
			LOG.info(pluginName + " received confirmation: " + confirmed);
		return confirmed;
	}

	protected void sendPseudonym(BdfWriter w, byte[] nonce)
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

	protected Author receivePseudonym(BdfReader r, byte[] nonce)
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

	protected void sendTimestamp(BdfWriter w, long timestamp)
			throws IOException {
		w.writeLong(timestamp);
		w.flush();
		if (LOG.isLoggable(INFO)) LOG.info(pluginName + " sent timestamp");
	}

	protected long receiveTimestamp(BdfReader r) throws IOException {
		long timestamp = r.readLong();
		if (timestamp < 0) throw new FormatException();
		if (LOG.isLoggable(INFO)) LOG.info(pluginName + " received timestamp");
		return timestamp;
	}

	protected ContactId addContact(Author remoteAuthor, SecretKey master,
			long timestamp, boolean alice) throws DbException {
		// Add the contact to the database
		contactId = contactManager.addContact(remoteAuthor,
				localAuthor.getId(), master, timestamp, alice, true);
		return contactId;
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
