package org.briarproject.bramble.contact;

import org.briarproject.bramble.api.FormatException;
import org.briarproject.bramble.api.Pair;
import org.briarproject.bramble.api.contact.ContactManager;
import org.briarproject.bramble.api.contact.HandshakeManager;
import org.briarproject.bramble.api.contact.PendingContact;
import org.briarproject.bramble.api.contact.PendingContactId;
import org.briarproject.bramble.api.crypto.AgreementPublicKey;
import org.briarproject.bramble.api.crypto.KeyPair;
import org.briarproject.bramble.api.crypto.PublicKey;
import org.briarproject.bramble.api.crypto.SecretKey;
import org.briarproject.bramble.api.crypto.TransportCrypto;
import org.briarproject.bramble.api.db.DbException;
import org.briarproject.bramble.api.db.TransactionManager;
import org.briarproject.bramble.api.identity.IdentityManager;
import org.briarproject.bramble.api.record.Record;
import org.briarproject.bramble.api.record.RecordReader;
import org.briarproject.bramble.api.record.RecordReader.RecordPredicate;
import org.briarproject.bramble.api.record.RecordReaderFactory;
import org.briarproject.bramble.api.record.RecordWriter;
import org.briarproject.bramble.api.record.RecordWriterFactory;
import org.briarproject.bramble.api.transport.StreamWriter;
import org.briarproject.nullsafety.NotNullByDefault;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.security.GeneralSecurityException;
import java.util.List;

import javax.annotation.concurrent.Immutable;
import javax.inject.Inject;

import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static org.briarproject.bramble.api.crypto.CryptoConstants.MAX_AGREEMENT_PUBLIC_KEY_BYTES;
import static org.briarproject.bramble.contact.HandshakeConstants.PROOF_BYTES;
import static org.briarproject.bramble.contact.HandshakeConstants.PROTOCOL_MAJOR_VERSION;
import static org.briarproject.bramble.contact.HandshakeConstants.PROTOCOL_MINOR_VERSION;
import static org.briarproject.bramble.contact.HandshakeRecordTypes.RECORD_TYPE_EPHEMERAL_PUBLIC_KEY;
import static org.briarproject.bramble.contact.HandshakeRecordTypes.RECORD_TYPE_MINOR_VERSION;
import static org.briarproject.bramble.contact.HandshakeRecordTypes.RECORD_TYPE_PROOF_OF_OWNERSHIP;
import static org.briarproject.bramble.util.ValidationUtils.checkLength;

@Immutable
@NotNullByDefault
class HandshakeManagerImpl implements HandshakeManager {

	// Ignore records with current protocol version, unknown record type
	private static final RecordPredicate IGNORE = r ->
			r.getProtocolVersion() == PROTOCOL_MAJOR_VERSION &&
					!isKnownRecordType(r.getRecordType());

	private static boolean isKnownRecordType(byte type) {
		return type == RECORD_TYPE_EPHEMERAL_PUBLIC_KEY ||
				type == RECORD_TYPE_PROOF_OF_OWNERSHIP ||
				type == RECORD_TYPE_MINOR_VERSION;
	}

	private final TransactionManager db;
	private final IdentityManager identityManager;
	private final ContactManager contactManager;
	private final TransportCrypto transportCrypto;
	private final HandshakeCrypto handshakeCrypto;
	private final RecordReaderFactory recordReaderFactory;
	private final RecordWriterFactory recordWriterFactory;

	@Inject
	HandshakeManagerImpl(TransactionManager db,
			IdentityManager identityManager,
			ContactManager contactManager,
			TransportCrypto transportCrypto,
			HandshakeCrypto handshakeCrypto,
			RecordReaderFactory recordReaderFactory,
			RecordWriterFactory recordWriterFactory) {
		this.db = db;
		this.identityManager = identityManager;
		this.contactManager = contactManager;
		this.transportCrypto = transportCrypto;
		this.handshakeCrypto = handshakeCrypto;
		this.recordReaderFactory = recordReaderFactory;
		this.recordWriterFactory = recordWriterFactory;
	}

	@Override
	public HandshakeResult handshake(PendingContactId p, InputStream in,
			StreamWriter out) throws DbException, IOException {
		Pair<PublicKey, KeyPair> keys = db.transactionWithResult(true, txn -> {
			PendingContact pendingContact =
					contactManager.getPendingContact(txn, p);
			KeyPair keyPair = identityManager.getHandshakeKeys(txn);
			return new Pair<>(pendingContact.getPublicKey(), keyPair);
		});
		PublicKey theirStaticPublicKey = keys.getFirst();
		KeyPair ourStaticKeyPair = keys.getSecond();
		boolean alice = transportCrypto.isAlice(theirStaticPublicKey,
				ourStaticKeyPair);
		RecordReader recordReader = recordReaderFactory.createRecordReader(in);
		RecordWriter recordWriter = recordWriterFactory
				.createRecordWriter(out.getOutputStream());
		KeyPair ourEphemeralKeyPair =
				handshakeCrypto.generateEphemeralKeyPair();
		Pair<Byte, PublicKey> theirMinorVersionAndKey;
		if (alice) {
			sendMinorVersion(recordWriter);
			sendPublicKey(recordWriter, ourEphemeralKeyPair.getPublic());
			theirMinorVersionAndKey = receiveMinorVersionAndKey(recordReader);
		} else {
			theirMinorVersionAndKey = receiveMinorVersionAndKey(recordReader);
			sendMinorVersion(recordWriter);
			sendPublicKey(recordWriter, ourEphemeralKeyPair.getPublic());
		}
		byte theirMinorVersion = theirMinorVersionAndKey.getFirst();
		PublicKey theirEphemeralPublicKey = theirMinorVersionAndKey.getSecond();
		SecretKey masterKey;
		try {
			if (theirMinorVersion > 0) {
				masterKey = handshakeCrypto.deriveMasterKey_0_1(
						theirStaticPublicKey, theirEphemeralPublicKey,
						ourStaticKeyPair, ourEphemeralKeyPair, alice);
			} else {
				// TODO: Remove this branch after a reasonable migration
				//  period (added 2023-03-10).
				masterKey = handshakeCrypto.deriveMasterKey_0_0(
						theirStaticPublicKey, theirEphemeralPublicKey,
						ourStaticKeyPair, ourEphemeralKeyPair, alice);
			}
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
		return new HandshakeResult(masterKey, alice);
	}

	private void sendPublicKey(RecordWriter w, PublicKey k) throws IOException {
		w.writeRecord(new Record(PROTOCOL_MAJOR_VERSION,
				RECORD_TYPE_EPHEMERAL_PUBLIC_KEY, k.getEncoded()));
		w.flush();
	}

	/**
	 * Receives the remote peer's protocol minor version and ephemeral public
	 * key.
	 * <p>
	 * In version 0.1 of the protocol, each peer sends a minor version record
	 * followed by an ephemeral public key record.
	 * <p>
	 * In version 0.0 of the protocol, each peer sends an ephemeral public key
	 * record without a preceding minor version record.
	 * <p>
	 * Therefore the remote peer's minor version must be non-zero if a minor
	 * version record is received, and is assumed to be zero if no minor
	 * version record is received.
	 */
	private Pair<Byte, PublicKey> receiveMinorVersionAndKey(RecordReader r)
			throws IOException {
		byte theirMinorVersion;
		PublicKey theirEphemeralPublicKey;
		// The first record can be either a minor version record or an
		// ephemeral public key record
		Record first = readRecord(r, asList(RECORD_TYPE_MINOR_VERSION,
				RECORD_TYPE_EPHEMERAL_PUBLIC_KEY));
		if (first.getRecordType() == RECORD_TYPE_MINOR_VERSION) {
			// The payload must be a single byte giving the remote peer's
			// protocol minor version, which must be non-zero
			byte[] payload = first.getPayload();
			checkLength(payload, 1);
			theirMinorVersion = payload[0];
			if (theirMinorVersion == 0) throw new FormatException();
			// The second record must be an ephemeral public key record
			Record second = readRecord(r,
					singletonList(RECORD_TYPE_EPHEMERAL_PUBLIC_KEY));
			theirEphemeralPublicKey = parsePublicKey(second);
		} else {
			// The remote peer did not send a minor version record, so the
			// remote peer's protocol minor version is assumed to be zero
			// TODO: Remove this branch after a reasonable migration period
			//  (added 2023-03-10).
			theirMinorVersion = 0;
			theirEphemeralPublicKey = parsePublicKey(first);
		}
		return new Pair<>(theirMinorVersion, theirEphemeralPublicKey);
	}

	private PublicKey parsePublicKey(Record rec) throws IOException {
		if (rec.getRecordType() != RECORD_TYPE_EPHEMERAL_PUBLIC_KEY) {
			throw new AssertionError();
		}
		byte[] key = rec.getPayload();
		checkLength(key, 1, MAX_AGREEMENT_PUBLIC_KEY_BYTES);
		return new AgreementPublicKey(key);
	}

	private void sendProof(RecordWriter w, byte[] proof) throws IOException {
		w.writeRecord(new Record(PROTOCOL_MAJOR_VERSION,
				RECORD_TYPE_PROOF_OF_OWNERSHIP, proof));
		w.flush();
	}

	private byte[] receiveProof(RecordReader r) throws IOException {
		Record rec = readRecord(r,
				singletonList(RECORD_TYPE_PROOF_OF_OWNERSHIP));
		byte[] proof = rec.getPayload();
		checkLength(proof, PROOF_BYTES, PROOF_BYTES);
		return proof;
	}

	private void sendMinorVersion(RecordWriter w) throws IOException {
		w.writeRecord(new Record(PROTOCOL_MAJOR_VERSION,
				RECORD_TYPE_MINOR_VERSION,
				new byte[] {PROTOCOL_MINOR_VERSION}));
		w.flush();
	}

	private Record readRecord(RecordReader r, List<Byte> expectedTypes)
			throws IOException {
		// Accept records with current protocol version, expected types only
		RecordPredicate accept = rec ->
				rec.getProtocolVersion() == PROTOCOL_MAJOR_VERSION &&
						expectedTypes.contains(rec.getRecordType());
		Record rec = r.readRecord(accept, IGNORE);
		if (rec == null) throw new EOFException();
		return rec;
	}
}
