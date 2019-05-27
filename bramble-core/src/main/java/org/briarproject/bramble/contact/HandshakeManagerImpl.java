package org.briarproject.bramble.contact;

import org.briarproject.bramble.api.FormatException;
import org.briarproject.bramble.api.Pair;
import org.briarproject.bramble.api.Predicate;
import org.briarproject.bramble.api.contact.Contact;
import org.briarproject.bramble.api.contact.ContactExchangeManager;
import org.briarproject.bramble.api.contact.ContactManager;
import org.briarproject.bramble.api.contact.HandshakeManager;
import org.briarproject.bramble.api.contact.PendingContact;
import org.briarproject.bramble.api.contact.PendingContactId;
import org.briarproject.bramble.api.crypto.AgreementPublicKey;
import org.briarproject.bramble.api.crypto.KeyPair;
import org.briarproject.bramble.api.crypto.PublicKey;
import org.briarproject.bramble.api.crypto.SecretKey;
import org.briarproject.bramble.api.db.DatabaseComponent;
import org.briarproject.bramble.api.db.DbException;
import org.briarproject.bramble.api.db.TransactionManager;
import org.briarproject.bramble.api.identity.IdentityManager;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.plugin.duplex.DuplexTransportConnection;
import org.briarproject.bramble.api.record.Record;
import org.briarproject.bramble.api.record.RecordReader;
import org.briarproject.bramble.api.record.RecordReaderFactory;
import org.briarproject.bramble.api.record.RecordWriter;
import org.briarproject.bramble.api.record.RecordWriterFactory;
import org.briarproject.bramble.api.transport.StreamWriter;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.security.GeneralSecurityException;

import javax.annotation.concurrent.Immutable;
import javax.inject.Inject;

import static org.briarproject.bramble.api.crypto.CryptoConstants.MAX_AGREEMENT_PUBLIC_KEY_BYTES;
import static org.briarproject.bramble.contact.HandshakeConstants.PROTOCOL_VERSION;
import static org.briarproject.bramble.contact.HandshakeRecordTypes.EPHEMERAL_PUBLIC_KEY;
import static org.briarproject.bramble.contact.HandshakeRecordTypes.PROOF_OF_OWNERSHIP;

@Immutable
@NotNullByDefault
class HandshakeManagerImpl implements HandshakeManager {

	// Ignore records with current protocol version, unknown record type
	private static final Predicate<Record> IGNORE = r ->
			r.getProtocolVersion() == PROTOCOL_VERSION &&
					!isKnownRecordType(r.getRecordType());

	private static boolean isKnownRecordType(byte type) {
		return type == EPHEMERAL_PUBLIC_KEY || type == PROOF_OF_OWNERSHIP;
	}

	private final TransactionManager db;
	private final IdentityManager identityManager;
	private final ContactManager contactManager;
	private final HandshakeCrypto handshakeCrypto;
	private final RecordReaderFactory recordReaderFactory;
	private final RecordWriterFactory recordWriterFactory;
	private final ContactExchangeManager contactExchangeManager;

	@Inject
	HandshakeManagerImpl(DatabaseComponent db,
			IdentityManager identityManager,
			ContactManager contactManager,
			HandshakeCrypto handshakeCrypto,
			RecordReaderFactory recordReaderFactory,
			RecordWriterFactory recordWriterFactory,
			ContactExchangeManager contactExchangeManager) {
		this.db = db;
		this.identityManager = identityManager;
		this.contactManager = contactManager;
		this.handshakeCrypto = handshakeCrypto;
		this.recordReaderFactory = recordReaderFactory;
		this.recordWriterFactory = recordWriterFactory;
		this.contactExchangeManager = contactExchangeManager;
	}

	@Override
	public Contact handshakeAndAddContact(PendingContactId p,
			InputStream in, StreamWriter out, DuplexTransportConnection conn)
			throws DbException, IOException {
		Pair<PublicKey, KeyPair> keys = db.transactionWithResult(true, txn -> {
			PendingContact pendingContact =
					contactManager.getPendingContact(txn, p);
			KeyPair keyPair = identityManager.getHandshakeKeys(txn);
			return new Pair<>(pendingContact.getPublicKey(), keyPair);
		});
		PublicKey theirStaticPublicKey = keys.getFirst();
		KeyPair ourStaticKeyPair = keys.getSecond();
		boolean alice = handshakeCrypto.isLocalPeerAlice(theirStaticPublicKey,
				ourStaticKeyPair);
		RecordReader recordReader = recordReaderFactory.createRecordReader(in);
		RecordWriter recordWriter = recordWriterFactory
				.createRecordWriter(out.getOutputStream());
		KeyPair ourEphemeralKeyPair =
				handshakeCrypto.generateEphemeralKeyPair();
		PublicKey theirEphemeralPublicKey;
		if (alice) {
			sendPublicKey(recordWriter, ourEphemeralKeyPair.getPublic());
			theirEphemeralPublicKey = receivePublicKey(recordReader);
		} else {
			theirEphemeralPublicKey = receivePublicKey(recordReader);
			sendPublicKey(recordWriter, ourEphemeralKeyPair.getPublic());
		}
		SecretKey masterKey;
		try {
			masterKey = handshakeCrypto.deriveMasterKey(theirStaticPublicKey,
					theirEphemeralPublicKey, ourStaticKeyPair,
					ourEphemeralKeyPair, alice);
		} catch (GeneralSecurityException e) {
			throw new FormatException();
		}
		byte[] ourProof = handshakeCrypto.proveOwnership(masterKey, alice);
		byte[] theirProof;
		if (alice) {
			sendProof(recordWriter, ourProof);
			theirProof = receiveProof(recordReader);
		} else {
			theirProof = receiveProof(recordReader);
			sendProof(recordWriter, ourProof);
		}
		out.sendEndOfStream();
		recordReader.readRecord(r -> false, IGNORE);
		if (!handshakeCrypto.verifyOwnership(masterKey, !alice, theirProof))
			throw new FormatException();
		return contactExchangeManager.exchangeContacts(p, conn, masterKey,
				alice, false);
	}

	private void sendPublicKey(RecordWriter w, PublicKey k) throws IOException {
		w.writeRecord(new Record(PROTOCOL_VERSION, EPHEMERAL_PUBLIC_KEY,
				k.getEncoded()));
		w.flush();
	}

	private PublicKey receivePublicKey(RecordReader r) throws IOException {
		Record rec = readRecord(r, EPHEMERAL_PUBLIC_KEY);
		int length = rec.getPayload().length;
		if (length == 0 || length > MAX_AGREEMENT_PUBLIC_KEY_BYTES)
			throw new FormatException();
		return new AgreementPublicKey(rec.getPayload());
	}

	private void sendProof(RecordWriter w, byte[] proof) throws IOException {
		w.writeRecord(new Record(PROTOCOL_VERSION, PROOF_OF_OWNERSHIP, proof));
		w.flush();
	}

	private byte[] receiveProof(RecordReader r) throws IOException {
		return readRecord(r, PROOF_OF_OWNERSHIP).getPayload();
	}

	private Record readRecord(RecordReader r, byte expectedType)
			throws IOException {
		// Accept records with current protocol version, expected type only
		Predicate<Record> accept = rec ->
				rec.getProtocolVersion() == PROTOCOL_VERSION &&
						rec.getRecordType() == expectedType;
		Record rec = r.readRecord(accept, IGNORE);
		if (rec == null) throw new EOFException();
		return rec;
	}
}
