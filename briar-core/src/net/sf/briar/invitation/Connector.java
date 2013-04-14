package net.sf.briar.invitation;

import static java.util.logging.Level.INFO;
import static java.util.logging.Level.WARNING;
import static net.sf.briar.api.AuthorConstants.MAX_AUTHOR_NAME_LENGTH;
import static net.sf.briar.api.AuthorConstants.MAX_PUBLIC_KEY_LENGTH;
import static net.sf.briar.api.AuthorConstants.MAX_SIGNATURE_LENGTH;
import static net.sf.briar.api.TransportPropertyConstants.MAX_PROPERTIES_PER_TRANSPORT;
import static net.sf.briar.api.TransportPropertyConstants.MAX_PROPERTY_LENGTH;
import static net.sf.briar.api.invitation.InvitationConstants.CONNECTION_TIMEOUT;
import static net.sf.briar.api.invitation.InvitationConstants.HASH_LENGTH;
import static net.sf.briar.api.messaging.Rating.GOOD;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.Signature;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.logging.Logger;

import net.sf.briar.api.Author;
import net.sf.briar.api.AuthorFactory;
import net.sf.briar.api.ContactId;
import net.sf.briar.api.FormatException;
import net.sf.briar.api.LocalAuthor;
import net.sf.briar.api.TransportId;
import net.sf.briar.api.TransportProperties;
import net.sf.briar.api.UniqueId;
import net.sf.briar.api.clock.Clock;
import net.sf.briar.api.crypto.CryptoComponent;
import net.sf.briar.api.crypto.KeyManager;
import net.sf.briar.api.crypto.KeyParser;
import net.sf.briar.api.crypto.MessageDigest;
import net.sf.briar.api.crypto.PseudoRandom;
import net.sf.briar.api.db.DatabaseComponent;
import net.sf.briar.api.db.DbException;
import net.sf.briar.api.db.NoSuchTransportException;
import net.sf.briar.api.plugins.duplex.DuplexPlugin;
import net.sf.briar.api.plugins.duplex.DuplexTransportConnection;
import net.sf.briar.api.serial.Reader;
import net.sf.briar.api.serial.ReaderFactory;
import net.sf.briar.api.serial.Writer;
import net.sf.briar.api.serial.WriterFactory;
import net.sf.briar.api.transport.ConnectionReaderFactory;
import net.sf.briar.api.transport.ConnectionWriterFactory;
import net.sf.briar.api.transport.Endpoint;

abstract class Connector extends Thread {

	private static final Logger LOG =
			Logger.getLogger(Connector.class.getName());

	protected final CryptoComponent crypto;
	protected final DatabaseComponent db;
	protected final ReaderFactory readerFactory;
	protected final WriterFactory writerFactory;
	protected final ConnectionReaderFactory connectionReaderFactory;
	protected final ConnectionWriterFactory connectionWriterFactory;
	protected final AuthorFactory authorFactory;
	protected final KeyManager keyManager;
	protected final Clock clock;
	protected final ConnectorGroup group;
	protected final DuplexPlugin plugin;
	protected final LocalAuthor localAuthor;
	protected final Map<TransportId, TransportProperties> localProps;
	protected final PseudoRandom random;
	protected final String pluginName;

	private final KeyPair keyPair;
	private final KeyParser keyParser;
	private final MessageDigest messageDigest;

	Connector(CryptoComponent crypto, DatabaseComponent db,
			ReaderFactory readerFactory, WriterFactory writerFactory,
			ConnectionReaderFactory connectionReaderFactory,
			ConnectionWriterFactory connectionWriterFactory,
			AuthorFactory authorFactory, KeyManager keyManager, Clock clock,
			ConnectorGroup group, DuplexPlugin plugin, LocalAuthor localAuthor,
			Map<TransportId, TransportProperties> localProps,
			PseudoRandom random) {
		super("Connector");
		this.crypto = crypto;
		this.db = db;
		this.readerFactory = readerFactory;
		this.writerFactory = writerFactory;
		this.connectionReaderFactory = connectionReaderFactory;
		this.connectionWriterFactory = connectionWriterFactory;
		this.authorFactory = authorFactory;
		this.keyManager = keyManager;
		this.clock = clock;
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

	protected DuplexTransportConnection acceptIncomingConnection() {
		if(LOG.isLoggable(INFO))
			LOG.info(pluginName + " accepting incoming connection");
		return plugin.acceptInvitation(random, CONNECTION_TIMEOUT);
	}

	protected DuplexTransportConnection makeOutgoingConnection() {
		if(LOG.isLoggable(INFO))
			LOG.info(pluginName + " making outgoing connection");
		return plugin.sendInvitation(random, CONNECTION_TIMEOUT);
	}

	protected void waitForHalfTime(long halfTime) {
		long now = clock.currentTimeMillis();
		if(now < halfTime) {
			if(LOG.isLoggable(INFO))
				LOG.info(pluginName + " sleeping until half-time");
			try {
				clock.sleep(halfTime - now);
			} catch(InterruptedException e) {
				if(LOG.isLoggable(INFO)) LOG.info("Interrupted while sleeping");
				Thread.currentThread().interrupt();
				return;
			}
		}
	}

	protected void sendPublicKeyHash(Writer w) throws IOException {
		w.writeBytes(messageDigest.digest(keyPair.getPublic().getEncoded()));
		w.flush();
		if(LOG.isLoggable(INFO)) LOG.info(pluginName + " sent hash");
	}

	protected byte[] receivePublicKeyHash(Reader r) throws IOException {
		byte[] b = r.readBytes(HASH_LENGTH);
		if(b.length < HASH_LENGTH) throw new FormatException();
		if(LOG.isLoggable(INFO)) LOG.info(pluginName + " received hash");
		return b;
	}

	protected void sendPublicKey(Writer w) throws IOException {
		w.writeBytes(keyPair.getPublic().getEncoded());
		w.flush();
		if(LOG.isLoggable(INFO)) LOG.info(pluginName + " sent key");
	}

	protected byte[] receivePublicKey(Reader r) throws GeneralSecurityException,
	IOException {
		byte[] b = r.readBytes(MAX_PUBLIC_KEY_LENGTH);
		keyParser.parsePublicKey(b);
		if(LOG.isLoggable(INFO)) LOG.info(pluginName + " received key");
		return b;
	}

	protected byte[] deriveMasterSecret(byte[] hash, byte[] key, boolean alice)
			throws GeneralSecurityException {
		// Check that the hash matches the key
		if(!Arrays.equals(hash, messageDigest.digest(key))) {
			if(LOG.isLoggable(INFO))
				LOG.info(pluginName + " hash does not match key");
			throw new GeneralSecurityException();
		}
		//  Derive the master secret
		return crypto.deriveMasterSecret(key, keyPair, alice);
	}

	protected void sendConfirmation(Writer w) throws IOException,
	InterruptedException {
		boolean matched = group.waitForLocalConfirmationResult();
		w.writeBoolean(matched);
		w.flush();
		if(LOG.isLoggable(INFO)) LOG.info(pluginName + " sent confirmation");
	}

	protected boolean receiveConfirmation(Reader r) throws IOException {
		boolean matched = r.readBoolean();
		if(LOG.isLoggable(INFO))
			LOG.info(pluginName + " received confirmation");
		return matched;
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
		w.writeBytes(localAuthor.getPublicKey());
		w.writeBytes(sig);
		w.flush();
		if(LOG.isLoggable(INFO)) LOG.info(pluginName + " sent pseudonym");
	}

	protected Author receivePseudonym(Reader r, byte[] nonce)
			throws GeneralSecurityException, IOException {
		// Read the name, public key and signature
		String name = r.readString(MAX_AUTHOR_NAME_LENGTH);
		byte[] publicKey = r.readBytes(MAX_PUBLIC_KEY_LENGTH);
		byte[] sig = r.readBytes(MAX_SIGNATURE_LENGTH);
		if(LOG.isLoggable(INFO)) LOG.info(pluginName + " received pseudonym");
		// Verify the signature
		Signature signature = crypto.getSignature();
		KeyParser keyParser = crypto.getSignatureKeyParser();
		signature.initVerify(keyParser.parsePublicKey(publicKey));
		signature.update(nonce);
		if(!signature.verify(sig)) {
			if(LOG.isLoggable(INFO))
				LOG.info(pluginName + " invalid signature");
			throw new GeneralSecurityException();
		}
		return authorFactory.createAuthor(name, publicKey);
	}

	protected void sendTimestamp(Writer w, long timestamp) throws IOException {
		w.writeInt64(timestamp);
		w.flush();
		if(LOG.isLoggable(INFO)) LOG.info(pluginName + " sent timestamp");
	}

	protected long receiveTimestamp(Reader r) throws IOException {
		long timestamp = r.readInt64();
		if(timestamp < 0) throw new FormatException();
		if(LOG.isLoggable(INFO)) LOG.info(pluginName + " received timestamp");
		return timestamp;
	}

	protected void sendTransportProperties(Writer w) throws IOException {
		w.writeListStart();
		for(Entry<TransportId, TransportProperties> e : localProps.entrySet()) {
			w.writeBytes(e.getKey().getBytes());
			w.writeMap(e.getValue());
		}
		w.writeListEnd();
		w.flush();
		if(LOG.isLoggable(INFO))
			LOG.info(pluginName + " sent transport properties");
	}

	protected Map<TransportId, TransportProperties> receiveTransportProperties(
			Reader r) throws IOException {
		Map<TransportId, TransportProperties> remoteProps =
				new HashMap<TransportId, TransportProperties>();
		r.readListStart();
		while(!r.hasListEnd()) {
			byte[] b = r.readBytes(UniqueId.LENGTH);
			if(b.length != UniqueId.LENGTH) throw new FormatException();
			TransportId id = new TransportId(b);
			r.setMaxStringLength(MAX_PROPERTY_LENGTH);
			Map<String, String> p = r.readMap(String.class, String.class);
			r.resetMaxStringLength();
			if(p.size() > MAX_PROPERTIES_PER_TRANSPORT)
				throw new FormatException();
			remoteProps.put(id, new TransportProperties(p));
		}
		r.readListEnd();
		if(LOG.isLoggable(INFO))
			LOG.info(pluginName + " received transport properties");
		return remoteProps;
	}

	protected void addContact(Author remoteAuthor,
			Map<TransportId, TransportProperties> remoteProps,  byte[] secret,
			long epoch, boolean alice) throws DbException {
		// Add the contact to the database
		ContactId c = db.addContact(remoteAuthor, localAuthor.getId());
		// Add a positive rating for the contact's pseudonym
		db.setRating(remoteAuthor.getId(), GOOD);
		// Store the remote transport properties
		db.setRemoteProperties(c, remoteProps);
		// Create an endpoint for each transport shared with the contact
		List<TransportId> ids = new ArrayList<TransportId>();
		for(TransportId id : localProps.keySet())
			if(remoteProps.containsKey(id)) ids.add(id);
		// Assign indices to the transports deterministically and derive keys
		Collections.sort(ids, TransportIdComparator.INSTANCE);
		int size = ids.size();
		for(int i = 0; i < size; i++) {
			Endpoint ep = new Endpoint(c, ids.get(i), epoch, alice);
			try {
				db.addEndpoint(ep);
			} catch(NoSuchTransportException e) {
				continue;
			}
			keyManager.endpointAdded(ep, crypto.deriveInitialSecret(secret, i));
		}
	}

	protected void tryToClose(DuplexTransportConnection conn,
			boolean exception) {
		try {
			if(LOG.isLoggable(INFO)) LOG.info("Closing connection");
			conn.dispose(exception, true);
		} catch(IOException e) {
			if(LOG.isLoggable(WARNING)) LOG.log(WARNING, e.toString(), e);
		}
	}

	private static class TransportIdComparator
	implements Comparator<TransportId> {

		private static final TransportIdComparator INSTANCE =
				new TransportIdComparator();

		public int compare(TransportId t1, TransportId t2) {
			byte[] b1 = t1.getBytes(), b2 = t2.getBytes();
			for(int i = 0; i < UniqueId.LENGTH; i++) {
				if((b1[i] & 0xff) < (b2[i] & 0xff)) return -1;
				if((b1[i] & 0xff) > (b2[i] & 0xff)) return 1;
			}
			return 0;
		}
	}
}
