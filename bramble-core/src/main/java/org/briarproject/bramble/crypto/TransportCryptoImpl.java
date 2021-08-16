package org.briarproject.bramble.crypto;

import org.bouncycastle.crypto.Digest;
import org.bouncycastle.crypto.digests.Blake2bDigest;
import org.briarproject.bramble.api.crypto.CryptoComponent;
import org.briarproject.bramble.api.crypto.KeyPair;
import org.briarproject.bramble.api.crypto.PublicKey;
import org.briarproject.bramble.api.crypto.SecretKey;
import org.briarproject.bramble.api.crypto.TransportCrypto;
import org.briarproject.bramble.api.plugin.TransportId;
import org.briarproject.bramble.api.transport.IncomingKeys;
import org.briarproject.bramble.api.transport.OutgoingKeys;
import org.briarproject.bramble.api.transport.TransportKeys;

import java.security.GeneralSecurityException;

import javax.inject.Inject;

import static java.lang.System.arraycopy;
import static org.briarproject.bramble.api.Bytes.compare;
import static org.briarproject.bramble.api.transport.TransportConstants.ALICE_HANDSHAKE_HEADER_LABEL;
import static org.briarproject.bramble.api.transport.TransportConstants.ALICE_HANDSHAKE_TAG_LABEL;
import static org.briarproject.bramble.api.transport.TransportConstants.ALICE_HEADER_LABEL;
import static org.briarproject.bramble.api.transport.TransportConstants.ALICE_TAG_LABEL;
import static org.briarproject.bramble.api.transport.TransportConstants.BOB_HANDSHAKE_HEADER_LABEL;
import static org.briarproject.bramble.api.transport.TransportConstants.BOB_HANDSHAKE_TAG_LABEL;
import static org.briarproject.bramble.api.transport.TransportConstants.BOB_HEADER_LABEL;
import static org.briarproject.bramble.api.transport.TransportConstants.BOB_TAG_LABEL;
import static org.briarproject.bramble.api.transport.TransportConstants.CONTACT_ROOT_KEY_LABEL;
import static org.briarproject.bramble.api.transport.TransportConstants.PENDING_CONTACT_ROOT_KEY_LABEL;
import static org.briarproject.bramble.api.transport.TransportConstants.ROTATE_LABEL;
import static org.briarproject.bramble.api.transport.TransportConstants.STATIC_MASTER_KEY_LABEL;
import static org.briarproject.bramble.api.transport.TransportConstants.TAG_LENGTH;
import static org.briarproject.bramble.util.ByteUtils.INT_16_BYTES;
import static org.briarproject.bramble.util.ByteUtils.INT_64_BYTES;
import static org.briarproject.bramble.util.ByteUtils.MAX_16_BIT_UNSIGNED;
import static org.briarproject.bramble.util.ByteUtils.MAX_32_BIT_UNSIGNED;
import static org.briarproject.bramble.util.ByteUtils.writeUint16;
import static org.briarproject.bramble.util.ByteUtils.writeUint64;
import static org.briarproject.bramble.util.StringUtils.toUtf8;

class TransportCryptoImpl implements TransportCrypto {

	private final CryptoComponent crypto;

	@Inject
	TransportCryptoImpl(CryptoComponent crypto) {
		this.crypto = crypto;
	}

	@Override
	public boolean isAlice(PublicKey theirHandshakePublicKey,
			KeyPair ourHandshakeKeyPair) {
		byte[] theirPublic = theirHandshakePublicKey.getEncoded();
		byte[] ourPublic = ourHandshakeKeyPair.getPublic().getEncoded();
		return compare(ourPublic, theirPublic) < 0;
	}

	@Override
	public SecretKey deriveStaticMasterKey(PublicKey theirHandshakePublicKey,
			KeyPair ourHandshakeKeyPair) throws GeneralSecurityException {
		byte[] theirPublic = theirHandshakePublicKey.getEncoded();
		byte[] ourPublic = ourHandshakeKeyPair.getPublic().getEncoded();
		boolean alice = compare(ourPublic, theirPublic) < 0;
		byte[][] inputs = {
				alice ? ourPublic : theirPublic,
				alice ? theirPublic : ourPublic
		};
		return crypto.deriveSharedSecret(STATIC_MASTER_KEY_LABEL,
				theirHandshakePublicKey, ourHandshakeKeyPair, inputs);
	}

	@Override
	public SecretKey deriveHandshakeRootKey(SecretKey staticMasterKey,
			boolean pendingContact) {
		String label = pendingContact ?
				PENDING_CONTACT_ROOT_KEY_LABEL : CONTACT_ROOT_KEY_LABEL;
		return crypto.deriveKey(label, staticMasterKey);
	}

	@Override
	public TransportKeys deriveRotationKeys(TransportId t,
			SecretKey rootKey, long timePeriod, boolean weAreAlice,
			boolean active) {
		// Keys for the previous period are derived from the root key
		SecretKey inTagPrev = deriveTagKey(rootKey, t, !weAreAlice);
		SecretKey inHeaderPrev = deriveHeaderKey(rootKey, t, !weAreAlice);
		SecretKey outTagPrev = deriveTagKey(rootKey, t, weAreAlice);
		SecretKey outHeaderPrev = deriveHeaderKey(rootKey, t, weAreAlice);
		// Derive the keys for the current and next periods
		SecretKey inTagCurr = rotateKey(inTagPrev, timePeriod);
		SecretKey inHeaderCurr = rotateKey(inHeaderPrev, timePeriod);
		SecretKey inTagNext = rotateKey(inTagCurr, timePeriod + 1);
		SecretKey inHeaderNext = rotateKey(inHeaderCurr, timePeriod + 1);
		SecretKey outTagCurr = rotateKey(outTagPrev, timePeriod);
		SecretKey outHeaderCurr = rotateKey(outHeaderPrev, timePeriod);
		// Initialise the reordering windows and stream counters
		IncomingKeys inPrev = new IncomingKeys(inTagPrev, inHeaderPrev,
				timePeriod - 1);
		IncomingKeys inCurr = new IncomingKeys(inTagCurr, inHeaderCurr,
				timePeriod);
		IncomingKeys inNext = new IncomingKeys(inTagNext, inHeaderNext,
				timePeriod + 1);
		OutgoingKeys outCurr = new OutgoingKeys(outTagCurr, outHeaderCurr,
				timePeriod, active);
		// Collect and return the keys
		return new TransportKeys(t, inPrev, inCurr, inNext, outCurr);
	}

	private SecretKey rotateKey(SecretKey k, long timePeriod) {
		byte[] period = new byte[INT_64_BYTES];
		writeUint64(timePeriod, period, 0);
		return crypto.deriveKey(ROTATE_LABEL, k, period);
	}

	private SecretKey deriveTagKey(SecretKey rootKey, TransportId t,
			boolean keyBelongsToAlice) {
		String label = keyBelongsToAlice ? ALICE_TAG_LABEL : BOB_TAG_LABEL;
		byte[] id = toUtf8(t.getString());
		return crypto.deriveKey(label, rootKey, id);
	}

	private SecretKey deriveHeaderKey(SecretKey rootKey, TransportId t,
			boolean keyBelongsToAlice) {
		String label = keyBelongsToAlice ? ALICE_HEADER_LABEL :
				BOB_HEADER_LABEL;
		byte[] id = toUtf8(t.getString());
		return crypto.deriveKey(label, rootKey, id);
	}

	@Override
	public TransportKeys deriveHandshakeKeys(TransportId t, SecretKey rootKey,
			long timePeriod, boolean weAreAlice) {
		if (timePeriod < 1) throw new IllegalArgumentException();
		IncomingKeys inPrev = deriveIncomingHandshakeKeys(t, rootKey,
				weAreAlice, timePeriod - 1);
		IncomingKeys inCurr = deriveIncomingHandshakeKeys(t, rootKey,
				weAreAlice, timePeriod);
		IncomingKeys inNext = deriveIncomingHandshakeKeys(t, rootKey,
				weAreAlice, timePeriod + 1);
		OutgoingKeys outCurr = deriveOutgoingHandshakeKeys(t, rootKey,
				weAreAlice, timePeriod);
		return new TransportKeys(t, inPrev, inCurr, inNext, outCurr, rootKey,
				weAreAlice);
	}

	private IncomingKeys deriveIncomingHandshakeKeys(TransportId t,
			SecretKey rootKey, boolean weAreAlice, long timePeriod) {
		SecretKey tag = deriveHandshakeTagKey(t, rootKey, !weAreAlice,
				timePeriod);
		SecretKey header = deriveHandshakeHeaderKey(t, rootKey, !weAreAlice,
				timePeriod);
		return new IncomingKeys(tag, header, timePeriod);
	}

	private OutgoingKeys deriveOutgoingHandshakeKeys(TransportId t,
			SecretKey rootKey, boolean weAreAlice, long timePeriod) {
		SecretKey tag = deriveHandshakeTagKey(t, rootKey, weAreAlice,
				timePeriod);
		SecretKey header = deriveHandshakeHeaderKey(t, rootKey, weAreAlice,
				timePeriod);
		return new OutgoingKeys(tag, header, timePeriod, true);
	}

	private SecretKey deriveHandshakeTagKey(TransportId t, SecretKey rootKey,
			boolean keyBelongsToAlice, long timePeriod) {
		String label = keyBelongsToAlice ? ALICE_HANDSHAKE_TAG_LABEL :
				BOB_HANDSHAKE_TAG_LABEL;
		byte[] id = toUtf8(t.getString());
		byte[] period = new byte[INT_64_BYTES];
		writeUint64(timePeriod, period, 0);
		return crypto.deriveKey(label, rootKey, id, period);
	}

	private SecretKey deriveHandshakeHeaderKey(TransportId t, SecretKey rootKey,
			boolean keyBelongsToAlice, long timePeriod) {
		String label = keyBelongsToAlice ? ALICE_HANDSHAKE_HEADER_LABEL :
				BOB_HANDSHAKE_HEADER_LABEL;
		byte[] id = toUtf8(t.getString());
		byte[] period = new byte[INT_64_BYTES];
		writeUint64(timePeriod, period, 0);
		return crypto.deriveKey(label, rootKey, id, period);
	}

	@Override
	public TransportKeys updateTransportKeys(TransportKeys k, long timePeriod) {
		if (k.isHandshakeMode()) return updateHandshakeKeys(k, timePeriod);
		else return updateRotationKeys(k, timePeriod);
	}

	private TransportKeys updateHandshakeKeys(TransportKeys k,
			long timePeriod) {
		long elapsed = timePeriod - k.getTimePeriod();
		TransportId t = k.getTransportId();
		SecretKey rootKey = k.getRootKey();
		boolean weAreAlice = k.isAlice();
		if (elapsed <= 0) {
			// The keys are for the given period or later - don't update them
			return k;
		} else if (elapsed == 1) {
			// The keys are one period old - shift by one period, keeping the
			// reordering windows for keys we retain
			IncomingKeys inPrev = k.getCurrentIncomingKeys();
			IncomingKeys inCurr = k.getNextIncomingKeys();
			IncomingKeys inNext = deriveIncomingHandshakeKeys(t, rootKey,
					weAreAlice, timePeriod + 1);
			OutgoingKeys outCurr = deriveOutgoingHandshakeKeys(t, rootKey,
					weAreAlice, timePeriod);
			return new TransportKeys(t, inPrev, inCurr, inNext, outCurr,
					rootKey, weAreAlice);
		} else if (elapsed == 2) {
			// The keys are two periods old - shift by two periods, keeping
			// the reordering windows for keys we retain
			IncomingKeys inPrev = k.getNextIncomingKeys();
			IncomingKeys inCurr = deriveIncomingHandshakeKeys(t, rootKey,
					weAreAlice, timePeriod);
			IncomingKeys inNext = deriveIncomingHandshakeKeys(t, rootKey,
					weAreAlice, timePeriod + 1);
			OutgoingKeys outCurr = deriveOutgoingHandshakeKeys(t, rootKey,
					weAreAlice, timePeriod);
			return new TransportKeys(t, inPrev, inCurr, inNext, outCurr,
					rootKey, weAreAlice);
		} else {
			// The keys are more than two periods old - derive fresh keys
			return deriveHandshakeKeys(t, rootKey, timePeriod, weAreAlice);
		}
	}

	private TransportKeys updateRotationKeys(TransportKeys k, long timePeriod) {
		if (k.getTimePeriod() >= timePeriod) return k;
		IncomingKeys inPrev = k.getPreviousIncomingKeys();
		IncomingKeys inCurr = k.getCurrentIncomingKeys();
		IncomingKeys inNext = k.getNextIncomingKeys();
		OutgoingKeys outCurr = k.getCurrentOutgoingKeys();
		long startPeriod = outCurr.getTimePeriod();
		boolean active = outCurr.isActive();
		// Rotate the keys
		for (long p = startPeriod + 1; p <= timePeriod; p++) {
			inPrev = inCurr;
			inCurr = inNext;
			SecretKey inNextTag = rotateKey(inNext.getTagKey(), p + 1);
			SecretKey inNextHeader = rotateKey(inNext.getHeaderKey(), p + 1);
			inNext = new IncomingKeys(inNextTag, inNextHeader, p + 1);
			SecretKey outCurrTag = rotateKey(outCurr.getTagKey(), p);
			SecretKey outCurrHeader = rotateKey(outCurr.getHeaderKey(), p);
			outCurr = new OutgoingKeys(outCurrTag, outCurrHeader, p, active);
		}
		// Collect and return the keys
		return new TransportKeys(k.getTransportId(), inPrev, inCurr, inNext,
				outCurr);
	}

	@Override
	public void encodeTag(byte[] tag, SecretKey tagKey, int protocolVersion,
			long streamNumber) {
		if (tag.length < TAG_LENGTH) throw new IllegalArgumentException();
		if (protocolVersion < 0 || protocolVersion > MAX_16_BIT_UNSIGNED)
			throw new IllegalArgumentException();
		if (streamNumber < 0 || streamNumber > MAX_32_BIT_UNSIGNED)
			throw new IllegalArgumentException();
		// Initialise the PRF
		Digest prf = new Blake2bDigest(tagKey.getBytes(), 32, null, null);
		// The output of the PRF must be long enough to use as a tag
		int macLength = prf.getDigestSize();
		if (macLength < TAG_LENGTH) throw new IllegalStateException();
		// The input is the protocol version as a 16-bit integer, followed by
		// the stream number as a 64-bit integer
		byte[] protocolVersionBytes = new byte[INT_16_BYTES];
		writeUint16(protocolVersion, protocolVersionBytes, 0);
		prf.update(protocolVersionBytes, 0, protocolVersionBytes.length);
		byte[] streamNumberBytes = new byte[INT_64_BYTES];
		writeUint64(streamNumber, streamNumberBytes, 0);
		prf.update(streamNumberBytes, 0, streamNumberBytes.length);
		byte[] mac = new byte[macLength];
		prf.doFinal(mac, 0);
		// The output is the first TAG_LENGTH bytes of the MAC
		arraycopy(mac, 0, tag, 0, TAG_LENGTH);
	}
}
