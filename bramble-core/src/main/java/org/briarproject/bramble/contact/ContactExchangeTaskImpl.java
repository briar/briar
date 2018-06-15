package org.briarproject.bramble.contact;

import org.briarproject.bramble.api.FormatException;
import org.briarproject.bramble.api.client.ClientHelper;
import org.briarproject.bramble.api.contact.ContactExchangeListener;
import org.briarproject.bramble.api.contact.ContactExchangeTask;
import org.briarproject.bramble.api.contact.ContactId;
import org.briarproject.bramble.api.contact.ContactManager;
import org.briarproject.bramble.api.crypto.CryptoComponent;
import org.briarproject.bramble.api.crypto.SecretKey;
import org.briarproject.bramble.api.data.BdfDictionary;
import org.briarproject.bramble.api.data.BdfList;
import org.briarproject.bramble.api.db.ContactExistsException;
import org.briarproject.bramble.api.db.DatabaseComponent;
import org.briarproject.bramble.api.db.DbException;
import org.briarproject.bramble.api.db.Transaction;
import org.briarproject.bramble.api.identity.Author;
import org.briarproject.bramble.api.identity.LocalAuthor;
import org.briarproject.bramble.api.nullsafety.MethodsNotNullByDefault;
import org.briarproject.bramble.api.nullsafety.ParametersNotNullByDefault;
import org.briarproject.bramble.api.plugin.ConnectionManager;
import org.briarproject.bramble.api.plugin.TransportId;
import org.briarproject.bramble.api.plugin.duplex.DuplexTransportConnection;
import org.briarproject.bramble.api.properties.TransportProperties;
import org.briarproject.bramble.api.properties.TransportPropertyManager;
import org.briarproject.bramble.api.record.Record;
import org.briarproject.bramble.api.record.RecordReader;
import org.briarproject.bramble.api.record.RecordReaderFactory;
import org.briarproject.bramble.api.record.RecordWriter;
import org.briarproject.bramble.api.record.RecordWriterFactory;
import org.briarproject.bramble.api.system.Clock;
import org.briarproject.bramble.api.transport.StreamReaderFactory;
import org.briarproject.bramble.api.transport.StreamWriter;
import org.briarproject.bramble.api.transport.StreamWriterFactory;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.GeneralSecurityException;
import java.util.Map;
import java.util.logging.Logger;

import javax.inject.Inject;

import static java.util.logging.Level.WARNING;
import static org.briarproject.bramble.api.contact.RecordTypes.CONTACT_INFO;
import static org.briarproject.bramble.api.identity.AuthorConstants.MAX_SIGNATURE_LENGTH;
import static org.briarproject.bramble.util.LogUtils.logException;
import static org.briarproject.bramble.util.ValidationUtils.checkLength;
import static org.briarproject.bramble.util.ValidationUtils.checkSize;

@MethodsNotNullByDefault
@ParametersNotNullByDefault
class ContactExchangeTaskImpl extends Thread implements ContactExchangeTask {

	private static final Logger LOG =
			Logger.getLogger(ContactExchangeTaskImpl.class.getName());

	private static final String SIGNING_LABEL_EXCHANGE =
			"org.briarproject.briar.contact/EXCHANGE";

	private final DatabaseComponent db;
	private final ClientHelper clientHelper;
	private final RecordReaderFactory recordReaderFactory;
	private final RecordWriterFactory recordWriterFactory;
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
	ContactExchangeTaskImpl(DatabaseComponent db, ClientHelper clientHelper,
			RecordReaderFactory recordReaderFactory,
			RecordWriterFactory recordWriterFactory, Clock clock,
			ConnectionManager connectionManager, ContactManager contactManager,
			TransportPropertyManager transportPropertyManager,
			CryptoComponent crypto, StreamReaderFactory streamReaderFactory,
			StreamWriterFactory streamWriterFactory) {
		this.db = db;
		this.clientHelper = clientHelper;
		this.recordReaderFactory = recordReaderFactory;
		this.recordWriterFactory = recordWriterFactory;
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
			logException(LOG, WARNING, e);
			listener.contactExchangeFailed();
			tryToClose(conn);
			return;
		}

		// Get the local transport properties
		Map<TransportId, TransportProperties> localProperties;
		try {
			localProperties = transportPropertyManager.getLocalProperties();
		} catch (DbException e) {
			logException(LOG, WARNING, e);
			listener.contactExchangeFailed();
			tryToClose(conn);
			return;
		}

		// Derive the header keys for the transport streams
		SecretKey aliceHeaderKey = crypto.deriveKey(ALICE_KEY_LABEL,
				masterSecret, new byte[] {PROTOCOL_VERSION});
		SecretKey bobHeaderKey = crypto.deriveKey(BOB_KEY_LABEL, masterSecret,
				new byte[] {PROTOCOL_VERSION});

		// Create the readers
		InputStream streamReader =
				streamReaderFactory.createContactExchangeStreamReader(in,
						alice ? bobHeaderKey : aliceHeaderKey);
		RecordReader recordReader =
				recordReaderFactory.createRecordReader(streamReader);

		// Create the writers
		StreamWriter streamWriter =
				streamWriterFactory.createContactExchangeStreamWriter(out,
						alice ? aliceHeaderKey : bobHeaderKey);
		RecordWriter recordWriter =
				recordWriterFactory.createRecordWriter(streamWriter.getOutputStream());

		// Derive the nonces to be signed
		byte[] aliceNonce = crypto.mac(ALICE_NONCE_LABEL, masterSecret,
				new byte[] {PROTOCOL_VERSION});
		byte[] bobNonce = crypto.mac(BOB_NONCE_LABEL, masterSecret,
				new byte[] {PROTOCOL_VERSION});
		byte[] localNonce = alice ? aliceNonce : bobNonce;
		byte[] remoteNonce = alice ? bobNonce : aliceNonce;

		// Sign the nonce
		byte[] localSignature = sign(localAuthor, localNonce);

		// Exchange contact info
		long localTimestamp = clock.currentTimeMillis();
		ContactInfo remoteInfo;
		try {
			if (alice) {
				sendContactInfo(recordWriter, localAuthor, localProperties,
						localSignature, localTimestamp);
				recordWriter.flush();
				remoteInfo = receiveContactInfo(recordReader);
			} else {
				remoteInfo = receiveContactInfo(recordReader);
				sendContactInfo(recordWriter, localAuthor, localProperties,
						localSignature, localTimestamp);
				recordWriter.flush();
			}
			// Send EOF on the outgoing stream
			streamWriter.sendEndOfStream();
			// Skip any remaining records from the incoming stream
			try {
				while (true) recordReader.readRecord();
			} catch (EOFException expected) {
				LOG.info("End of stream");
			}
		} catch (IOException e) {
			logException(LOG, WARNING, e);
			listener.contactExchangeFailed();
			tryToClose(conn);
			return;
		}

		// Verify the contact's signature
		if (!verify(remoteInfo.author, remoteNonce, remoteInfo.signature)) {
			LOG.warning("Invalid signature");
			listener.contactExchangeFailed();
			tryToClose(conn);
			return;
		}

		// The agreed timestamp is the minimum of the peers' timestamps
		long timestamp = Math.min(localTimestamp, remoteInfo.timestamp);

		try {
			// Add the contact
			ContactId contactId = addContact(remoteInfo.author, timestamp,
					remoteInfo.properties);
			// Reuse the connection as a transport connection
			connectionManager.manageOutgoingConnection(contactId, transportId,
					conn);
			// Pseudonym exchange succeeded
			LOG.info("Pseudonym exchange succeeded");
			listener.contactExchangeSucceeded(remoteInfo.author);
		} catch (ContactExistsException e) {
			logException(LOG, WARNING, e);
			tryToClose(conn);
			listener.duplicateContact(remoteInfo.author);
		} catch (DbException e) {
			logException(LOG, WARNING, e);
			tryToClose(conn);
			listener.contactExchangeFailed();
		}
	}

	private byte[] sign(LocalAuthor author, byte[] nonce) {
		try {
			return crypto.sign(SIGNING_LABEL_EXCHANGE, nonce,
					author.getPrivateKey());
		} catch (GeneralSecurityException e) {
			throw new AssertionError();
		}
	}

	private boolean verify(Author author, byte[] nonce, byte[] signature) {
		try {
			return crypto.verifySignature(signature, SIGNING_LABEL_EXCHANGE,
					nonce, author.getPublicKey());
		} catch (GeneralSecurityException e) {
			return false;
		}
	}

	private void sendContactInfo(RecordWriter recordWriter, Author author,
			Map<TransportId, TransportProperties> properties, byte[] signature,
			long timestamp) throws IOException {
		BdfList authorList = clientHelper.toList(author);
		BdfDictionary props = clientHelper.toDictionary(properties);
		BdfList payload = BdfList.of(authorList, props, signature, timestamp);
		recordWriter.writeRecord(new Record(PROTOCOL_VERSION, CONTACT_INFO,
				clientHelper.toByteArray(payload)));
		LOG.info("Sent contact info");
	}

	private ContactInfo receiveContactInfo(RecordReader recordReader)
			throws IOException {
		Record record;
		do {
			record = recordReader.readRecord();
			if (record.getProtocolVersion() != PROTOCOL_VERSION)
				throw new FormatException();
		} while (record.getRecordType() != CONTACT_INFO);
		LOG.info("Received contact info");
		BdfList payload = clientHelper.toList(record.getPayload());
		checkSize(payload, 4);
		Author author = clientHelper.parseAndValidateAuthor(payload.getList(0));
		BdfDictionary props = payload.getDictionary(1);
		Map<TransportId, TransportProperties> properties =
				clientHelper.parseAndValidateTransportPropertiesMap(props);
		byte[] signature = payload.getRaw(2);
		checkLength(signature, 1, MAX_SIGNATURE_LENGTH);
		long timestamp = payload.getLong(3);
		if (timestamp < 0) throw new FormatException();
		return new ContactInfo(author, properties, signature, timestamp);
	}

	private ContactId addContact(Author remoteAuthor, long timestamp,
			Map<TransportId, TransportProperties> remoteProperties)
			throws DbException {
		ContactId contactId;
		Transaction txn = db.startTransaction(false);
		try {
			contactId = contactManager.addContact(txn, remoteAuthor,
					localAuthor.getId(), masterSecret, timestamp, alice,
					true, true);
			transportPropertyManager.addRemoteProperties(txn, contactId,
					remoteProperties);
			db.commitTransaction(txn);
		} finally {
			db.endTransaction(txn);
		}
		return contactId;
	}

	private void tryToClose(DuplexTransportConnection conn) {
		try {
			LOG.info("Closing connection");
			conn.getReader().dispose(true, true);
			conn.getWriter().dispose(true);
		} catch (IOException e) {
			logException(LOG, WARNING, e);
		}
	}

	private static class ContactInfo {

		private final Author author;
		private final Map<TransportId, TransportProperties> properties;
		private final byte[] signature;
		private final long timestamp;

		private ContactInfo(Author author,
				Map<TransportId, TransportProperties> properties,
				byte[] signature, long timestamp) {
			this.author = author;
			this.properties = properties;
			this.signature = signature;
			this.timestamp = timestamp;
		}
	}
}
