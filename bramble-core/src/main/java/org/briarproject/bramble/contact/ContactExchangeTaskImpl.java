package org.briarproject.bramble.contact;

import org.briarproject.bramble.api.FormatException;
import org.briarproject.bramble.api.contact.ContactExchangeListener;
import org.briarproject.bramble.api.contact.ContactExchangeTask;
import org.briarproject.bramble.api.contact.ContactId;
import org.briarproject.bramble.api.contact.ContactManager;
import org.briarproject.bramble.api.crypto.CryptoComponent;
import org.briarproject.bramble.api.crypto.SecretKey;
import org.briarproject.bramble.api.data.BdfList;
import org.briarproject.bramble.api.data.BdfReader;
import org.briarproject.bramble.api.data.BdfReaderFactory;
import org.briarproject.bramble.api.data.BdfWriter;
import org.briarproject.bramble.api.data.BdfWriterFactory;
import org.briarproject.bramble.api.db.ContactExistsException;
import org.briarproject.bramble.api.db.DatabaseComponent;
import org.briarproject.bramble.api.db.DbException;
import org.briarproject.bramble.api.db.Transaction;
import org.briarproject.bramble.api.identity.Author;
import org.briarproject.bramble.api.identity.AuthorFactory;
import org.briarproject.bramble.api.identity.LocalAuthor;
import org.briarproject.bramble.api.nullsafety.MethodsNotNullByDefault;
import org.briarproject.bramble.api.nullsafety.ParametersNotNullByDefault;
import org.briarproject.bramble.api.plugin.ConnectionManager;
import org.briarproject.bramble.api.plugin.TransportId;
import org.briarproject.bramble.api.plugin.duplex.DuplexTransportConnection;
import org.briarproject.bramble.api.properties.TransportProperties;
import org.briarproject.bramble.api.properties.TransportPropertyManager;
import org.briarproject.bramble.api.system.Clock;
import org.briarproject.bramble.api.transport.StreamReaderFactory;
import org.briarproject.bramble.api.transport.StreamWriterFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.GeneralSecurityException;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.logging.Logger;

import javax.inject.Inject;

import static java.util.logging.Level.INFO;
import static java.util.logging.Level.WARNING;
import static org.briarproject.bramble.api.identity.AuthorConstants.MAX_AUTHOR_NAME_LENGTH;
import static org.briarproject.bramble.api.identity.AuthorConstants.MAX_PUBLIC_KEY_LENGTH;
import static org.briarproject.bramble.api.identity.AuthorConstants.MAX_SIGNATURE_LENGTH;
import static org.briarproject.bramble.api.plugin.TransportId.MAX_TRANSPORT_ID_LENGTH;
import static org.briarproject.bramble.api.properties.TransportPropertyConstants.MAX_PROPERTIES_PER_TRANSPORT;
import static org.briarproject.bramble.api.properties.TransportPropertyConstants.MAX_PROPERTY_LENGTH;

@MethodsNotNullByDefault
@ParametersNotNullByDefault
class ContactExchangeTaskImpl extends Thread implements ContactExchangeTask {

	private static final Logger LOG =
			Logger.getLogger(ContactExchangeTaskImpl.class.getName());

	private static final String SIGNING_LABEL_EXCHANGE =
			"org.briarproject.briar.contact/EXCHANGE";

	private final DatabaseComponent db;
	private final AuthorFactory authorFactory;
	private final BdfReaderFactory bdfReaderFactory;
	private final BdfWriterFactory bdfWriterFactory;
	private final Clock clock;
	private final ConnectionManager connectionManager;
	private final ContactManager contactManager;
	private final TransportPropertyManager transportPropertyManager;
	private final CryptoComponent crypto;
	private final StreamReaderFactory streamReaderFactory;
	private final StreamWriterFactory streamWriterFactory;

	private volatile ContactExchangeListener listener;
	private volatile LocalAuthor localAuthor;
	private volatile DuplexTransportConnection conn;
	private volatile TransportId transportId;
	private volatile SecretKey masterSecret;
	private volatile boolean alice;

	@Inject
	public ContactExchangeTaskImpl(DatabaseComponent db,
			AuthorFactory authorFactory, BdfReaderFactory bdfReaderFactory,
			BdfWriterFactory bdfWriterFactory, Clock clock,
			ConnectionManager connectionManager, ContactManager contactManager,
			TransportPropertyManager transportPropertyManager,
			CryptoComponent crypto, StreamReaderFactory streamReaderFactory,
			StreamWriterFactory streamWriterFactory) {
		this.db = db;
		this.authorFactory = authorFactory;
		this.bdfReaderFactory = bdfReaderFactory;
		this.bdfWriterFactory = bdfWriterFactory;
		this.clock = clock;
		this.connectionManager = connectionManager;
		this.contactManager = contactManager;
		this.transportPropertyManager = transportPropertyManager;
		this.crypto = crypto;
		this.streamReaderFactory = streamReaderFactory;
		this.streamWriterFactory = streamWriterFactory;
	}

	@Override
	public void startExchange(ContactExchangeListener listener,
			LocalAuthor localAuthor, SecretKey masterSecret,
			DuplexTransportConnection conn, TransportId transportId,
			boolean alice) {
		this.listener = listener;
		this.localAuthor = localAuthor;
		this.conn = conn;
		this.transportId = transportId;
		this.masterSecret = masterSecret;
		this.alice = alice;
		start();
	}

	@Override
	public void run() {
		// Get the transport connection's input and output streams
		InputStream in;
		OutputStream out;
		try {
			in = conn.getReader().getInputStream();
			out = conn.getWriter().getOutputStream();
		} catch (IOException e) {
			if (LOG.isLoggable(WARNING)) LOG.log(WARNING, e.toString(), e);
			listener.contactExchangeFailed();
			tryToClose(conn, true);
			return;
		}

		// Get the local transport properties
		Map<TransportId, TransportProperties> localProperties, remoteProperties;
		try {
			localProperties = transportPropertyManager.getLocalProperties();
		} catch (DbException e) {
			if (LOG.isLoggable(WARNING)) LOG.log(WARNING, e.toString(), e);
			listener.contactExchangeFailed();
			tryToClose(conn, true);
			return;
		}

		// Derive the header keys for the transport streams
		SecretKey aliceHeaderKey = crypto.deriveHeaderKey(masterSecret, true);
		SecretKey bobHeaderKey = crypto.deriveHeaderKey(masterSecret, false);

		// Create the readers
		InputStream streamReader =
				streamReaderFactory.createInvitationStreamReader(in,
						alice ? bobHeaderKey : aliceHeaderKey);
		BdfReader r = bdfReaderFactory.createReader(streamReader);
		// Create the writers
		OutputStream streamWriter =
				streamWriterFactory.createInvitationStreamWriter(out,
						alice ? aliceHeaderKey : bobHeaderKey);
		BdfWriter w = bdfWriterFactory.createWriter(streamWriter);

		// Derive the nonces to be signed
		byte[] aliceNonce = crypto.deriveSignatureNonce(masterSecret, true);
		byte[] bobNonce = crypto.deriveSignatureNonce(masterSecret, false);

		// Exchange pseudonyms, signed nonces, and timestamps
		long localTimestamp = clock.currentTimeMillis();
		Author remoteAuthor;
		long remoteTimestamp;
		try {
			if (alice) {
				sendPseudonym(w, aliceNonce);
				sendTimestamp(w, localTimestamp);
				sendTransportProperties(w, localProperties);
				w.flush();
				remoteAuthor = receivePseudonym(r, bobNonce);
				remoteTimestamp = receiveTimestamp(r);
				remoteProperties = receiveTransportProperties(r);
			} else {
				remoteAuthor = receivePseudonym(r, aliceNonce);
				remoteTimestamp = receiveTimestamp(r);
				remoteProperties = receiveTransportProperties(r);
				sendPseudonym(w, bobNonce);
				sendTimestamp(w, localTimestamp);
				sendTransportProperties(w, localProperties);
				w.flush();
			}
			// Close the outgoing stream and expect EOF on the incoming stream
			w.close();
			if (!r.eof()) LOG.warning("Unexpected data at end of connection");
		} catch (GeneralSecurityException e) {
			if (LOG.isLoggable(WARNING)) LOG.log(WARNING, e.toString(), e);
			listener.contactExchangeFailed();
			tryToClose(conn, true);
			return;
		} catch (IOException e) {
			if (LOG.isLoggable(WARNING)) LOG.log(WARNING, e.toString(), e);
			listener.contactExchangeFailed();
			tryToClose(conn, true);
			return;
		}

		// The agreed timestamp is the minimum of the peers' timestamps
		long timestamp = Math.min(localTimestamp, remoteTimestamp);

		try {
			// Add the contact
			ContactId contactId = addContact(remoteAuthor, masterSecret,
					timestamp, alice, remoteProperties);
			// Reuse the connection as a transport connection
			connectionManager.manageOutgoingConnection(contactId, transportId,
					conn);
			// Pseudonym exchange succeeded
			LOG.info("Pseudonym exchange succeeded");
			listener.contactExchangeSucceeded(remoteAuthor);
		} catch (ContactExistsException e) {
			if (LOG.isLoggable(WARNING)) LOG.log(WARNING, e.toString(), e);
			tryToClose(conn, true);
			listener.duplicateContact(remoteAuthor);
		} catch (DbException e) {
			if (LOG.isLoggable(WARNING)) LOG.log(WARNING, e.toString(), e);
			tryToClose(conn, true);
			listener.contactExchangeFailed();
		}
	}

	private void sendPseudonym(BdfWriter w, byte[] nonce)
			throws GeneralSecurityException, IOException {
		// Sign the nonce
		byte[] privateKey = localAuthor.getPrivateKey();
		byte[] sig = crypto.sign(SIGNING_LABEL_EXCHANGE, nonce, privateKey);

		// Write the name, public key and signature
		w.writeListStart();
		w.writeString(localAuthor.getName());
		w.writeRaw(localAuthor.getPublicKey());
		w.writeRaw(sig);
		w.writeListEnd();
		LOG.info("Sent pseudonym");
	}

	private Author receivePseudonym(BdfReader r, byte[] nonce)
			throws GeneralSecurityException, IOException {
		// Read the name, public key and signature
		r.readListStart();
		String name = r.readString(MAX_AUTHOR_NAME_LENGTH);
		byte[] publicKey = r.readRaw(MAX_PUBLIC_KEY_LENGTH);
		byte[] sig = r.readRaw(MAX_SIGNATURE_LENGTH);
		r.readListEnd();
		LOG.info("Received pseudonym");
		// Verify the signature
		if (!crypto.verify(SIGNING_LABEL_EXCHANGE, nonce, publicKey, sig)) {
			if (LOG.isLoggable(INFO))
				LOG.info("Invalid signature");
			throw new GeneralSecurityException();
		}
		return authorFactory.createAuthor(name, publicKey);
	}

	private void sendTimestamp(BdfWriter w, long timestamp)
			throws IOException {
		w.writeLong(timestamp);
		LOG.info("Sent timestamp");
	}

	private long receiveTimestamp(BdfReader r) throws IOException {
		long timestamp = r.readLong();
		if (timestamp < 0) throw new FormatException();
		LOG.info("Received timestamp");
		return timestamp;
	}

	private void sendTransportProperties(BdfWriter w,
			Map<TransportId, TransportProperties> local) throws IOException {
		w.writeListStart();
		for (Entry<TransportId, TransportProperties> e : local.entrySet())
			w.writeList(BdfList.of(e.getKey().getString(), e.getValue()));
		w.writeListEnd();
	}

	private Map<TransportId, TransportProperties> receiveTransportProperties(
			BdfReader r) throws IOException {
		Map<TransportId, TransportProperties> remote =
				new HashMap<TransportId, TransportProperties>();
		r.readListStart();
		while (!r.hasListEnd()) {
			r.readListStart();
			String id = r.readString(MAX_TRANSPORT_ID_LENGTH);
			if (id.isEmpty()) throw new FormatException();
			TransportProperties p = new TransportProperties();
			r.readDictionaryStart();
			while (!r.hasDictionaryEnd()) {
				if (p.size() == MAX_PROPERTIES_PER_TRANSPORT)
					throw new FormatException();
				String key = r.readString(MAX_PROPERTY_LENGTH);
				String value = r.readString(MAX_PROPERTY_LENGTH);
				p.put(key, value);
			}
			r.readDictionaryEnd();
			r.readListEnd();
			remote.put(new TransportId(id), p);
		}
		r.readListEnd();
		return remote;
	}

	private ContactId addContact(Author remoteAuthor, SecretKey master,
			long timestamp, boolean alice,
			Map<TransportId, TransportProperties> remoteProperties)
			throws DbException {
		ContactId contactId;
		Transaction txn = db.startTransaction(false);
		try {
			contactId = contactManager.addContact(txn, remoteAuthor,
					localAuthor.getId(), master, timestamp, alice, true, true);
			transportPropertyManager.addRemoteProperties(txn, contactId,
					remoteProperties);
			db.commitTransaction(txn);
		} finally {
			db.endTransaction(txn);
		}
		return contactId;
	}

	private void tryToClose(DuplexTransportConnection conn,
			boolean exception) {
		try {
			LOG.info("Closing connection");
			conn.getReader().dispose(exception, true);
			conn.getWriter().dispose(exception);
		} catch (IOException e) {
			if (LOG.isLoggable(WARNING)) LOG.log(WARNING, e.toString(), e);
		}
	}
}
