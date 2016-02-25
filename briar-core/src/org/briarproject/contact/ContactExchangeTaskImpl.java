package org.briarproject.contact;

import org.briarproject.api.FormatException;
import org.briarproject.api.TransportId;
import org.briarproject.api.contact.ContactExchangeListener;
import org.briarproject.api.contact.ContactExchangeTask;
import org.briarproject.api.contact.ContactId;
import org.briarproject.api.contact.ContactManager;
import org.briarproject.api.crypto.CryptoComponent;
import org.briarproject.api.crypto.KeyParser;
import org.briarproject.api.crypto.SecretKey;
import org.briarproject.api.crypto.Signature;
import org.briarproject.api.data.BdfReader;
import org.briarproject.api.data.BdfReaderFactory;
import org.briarproject.api.data.BdfWriter;
import org.briarproject.api.data.BdfWriterFactory;
import org.briarproject.api.db.ContactExistsException;
import org.briarproject.api.db.DbException;
import org.briarproject.api.identity.Author;
import org.briarproject.api.identity.AuthorFactory;
import org.briarproject.api.identity.LocalAuthor;
import org.briarproject.api.plugins.ConnectionManager;
import org.briarproject.api.plugins.duplex.DuplexTransportConnection;
import org.briarproject.api.system.Clock;
import org.briarproject.api.transport.KeyManager;
import org.briarproject.api.transport.StreamReaderFactory;
import org.briarproject.api.transport.StreamWriterFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.GeneralSecurityException;
import java.util.logging.Logger;

import static java.util.logging.Level.INFO;
import static java.util.logging.Level.WARNING;
import static org.briarproject.api.identity.AuthorConstants.MAX_AUTHOR_NAME_LENGTH;
import static org.briarproject.api.identity.AuthorConstants.MAX_PUBLIC_KEY_LENGTH;
import static org.briarproject.api.identity.AuthorConstants.MAX_SIGNATURE_LENGTH;

public class ContactExchangeTaskImpl extends Thread
		implements ContactExchangeTask {

	private static final Logger LOG =
			Logger.getLogger(ContactExchangeTaskImpl.class.getName());

	private final AuthorFactory authorFactory;
	private final BdfReaderFactory bdfReaderFactory;
	private final BdfWriterFactory bdfWriterFactory;
	private final Clock clock;
	private final ConnectionManager connectionManager;
	private final ContactManager contactManager;
	private final CryptoComponent crypto;
	private final KeyManager keyManager;
	private final StreamReaderFactory streamReaderFactory;
	private final StreamWriterFactory streamWriterFactory;

	private ContactExchangeListener listener;
	private LocalAuthor localAuthor;
	private DuplexTransportConnection conn;
	private TransportId transportId;
	private SecretKey masterSecret;
	private boolean alice;
	private boolean reuseConnection;

	public ContactExchangeTaskImpl(AuthorFactory authorFactory,
			BdfReaderFactory bdfReaderFactory,
			BdfWriterFactory bdfWriterFactory, Clock clock,
			ConnectionManager connectionManager, ContactManager contactManager,
			CryptoComponent crypto, KeyManager keyManager,
			StreamReaderFactory streamReaderFactory,
			StreamWriterFactory streamWriterFactory) {
		this.authorFactory = authorFactory;
		this.bdfReaderFactory = bdfReaderFactory;
		this.bdfWriterFactory = bdfWriterFactory;
		this.clock = clock;
		this.connectionManager = connectionManager;
		this.contactManager = contactManager;
		this.crypto = crypto;
		this.keyManager = keyManager;
		this.streamReaderFactory = streamReaderFactory;
		this.streamWriterFactory = streamWriterFactory;
	}

	@Override
	public void startExchange(ContactExchangeListener listener,
			LocalAuthor localAuthor, SecretKey masterSecret,
			DuplexTransportConnection conn, TransportId transportId,
			boolean alice, boolean reuseConnection) {
		this.listener = listener;
		this.localAuthor = localAuthor;
		this.conn = conn;
		this.transportId = transportId;
		this.masterSecret = masterSecret;
		this.alice = alice;
		this.reuseConnection = reuseConnection;
		start();
	}

	@Override
	public void run() {
		BdfReader r;
		BdfWriter w;
		try {
			// Create the readers
			InputStream streamReader =
					streamReaderFactory.createInvitationStreamReader(
							conn.getReader().getInputStream(),
							masterSecret);
			r = bdfReaderFactory.createReader(streamReader);
			// Create the writers
			OutputStream streamWriter =
					streamWriterFactory.createInvitationStreamWriter(
							conn.getWriter().getOutputStream(),
							masterSecret);
			w = bdfWriterFactory.createWriter(streamWriter);
		} catch (IOException e) {
			if (LOG.isLoggable(WARNING)) LOG.log(WARNING, e.toString(), e);
			listener.contactExchangeFailed();
			tryToClose(conn, true);
			return;
		}

		// Derive the invitation nonces
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
				remoteAuthor = receivePseudonym(r, bobNonce);
				remoteTimestamp = receiveTimestamp(r);
			} else {
				remoteAuthor = receivePseudonym(r, aliceNonce);
				remoteTimestamp = receiveTimestamp(r);
				sendPseudonym(w, bobNonce);
				sendTimestamp(w, localTimestamp);
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
			ContactId contactId =
					addContact(remoteAuthor, masterSecret, timestamp, true);
			// Reuse the connection as a transport connection
			if (reuseConnection)
				connectionManager
						.manageOutgoingConnection(contactId, transportId, conn);
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
		LOG.info("Sent pseudonym");
	}

	private Author receivePseudonym(BdfReader r, byte[] nonce)
			throws GeneralSecurityException, IOException {
		// Read the name, public key and signature
		String name = r.readString(MAX_AUTHOR_NAME_LENGTH);
		byte[] publicKey = r.readRaw(MAX_PUBLIC_KEY_LENGTH);
		byte[] sig = r.readRaw(MAX_SIGNATURE_LENGTH);
		LOG.info("Received pseudonym");
		// Verify the signature
		Signature signature = crypto.getSignature();
		KeyParser keyParser = crypto.getSignatureKeyParser();
		signature.initVerify(keyParser.parsePublicKey(publicKey));
		signature.update(nonce);
		if (!signature.verify(sig)) {
			if (LOG.isLoggable(INFO))
				LOG.info("Invalid signature");
			throw new GeneralSecurityException();
		}
		return authorFactory.createAuthor(name, publicKey);
	}

	private void sendTimestamp(BdfWriter w, long timestamp)
			throws IOException {
		w.writeLong(timestamp);
		w.flush();
		LOG.info("Sent timestamp");
	}

	private long receiveTimestamp(BdfReader r) throws IOException {
		long timestamp = r.readLong();
		if (timestamp < 0) throw new FormatException();
		LOG.info("Received timestamp");
		return timestamp;
	}

	private ContactId addContact(Author remoteAuthor, SecretKey master,
			long timestamp, boolean alice) throws DbException {
		// Add the contact to the database
		ContactId contactId = contactManager.addContact(remoteAuthor,
				localAuthor.getId(), master, timestamp, alice, true);
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
