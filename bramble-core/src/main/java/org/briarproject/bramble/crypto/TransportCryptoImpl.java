package org.briarproject.bramble.crypto;

import org.briarproject.bramble.api.crypto.CryptoComponent;
import org.briarproject.bramble.api.crypto.SecretKey;
import org.briarproject.bramble.api.crypto.TransportCrypto;
import org.briarproject.bramble.api.plugin.TransportId;
import org.briarproject.bramble.api.transport.IncomingKeys;
import org.briarproject.bramble.api.transport.OutgoingKeys;
import org.briarproject.bramble.api.transport.TransportKeys;
import org.briarproject.bramble.util.ByteUtils;
import org.briarproject.bramble.util.StringUtils;
import org.spongycastle.crypto.Digest;

import javax.inject.Inject;

import static org.briarproject.bramble.api.transport.TransportConstants.ALICE_HEADER_LABEL;
import static org.briarproject.bramble.api.transport.TransportConstants.ALICE_TAG_LABEL;
import static org.briarproject.bramble.api.transport.TransportConstants.BOB_HEADER_LABEL;
import static org.briarproject.bramble.api.transport.TransportConstants.BOB_TAG_LABEL;
import static org.briarproject.bramble.api.transport.TransportConstants.ROTATE_LABEL;
import static org.briarproject.bramble.api.transport.TransportConstants.TAG_LENGTH;
import static org.briarproject.bramble.util.ByteUtils.INT_16_BYTES;
import static org.briarproject.bramble.util.ByteUtils.INT_64_BYTES;
import static org.briarproject.bramble.util.ByteUtils.MAX_16_BIT_UNSIGNED;
import static org.briarproject.bramble.util.ByteUtils.MAX_32_BIT_UNSIGNED;

class TransportCryptoImpl implements TransportCrypto {

	private final CryptoComponent crypto;

	@Inject
	TransportCryptoImpl(CryptoComponent crypto) {
		this.crypto = crypto;
	}

	@Override
	public TransportKeys deriveTransportKeys(TransportId t,
			SecretKey master, long rotationPeriod, boolean alice) {
		// Keys for the previous period are derived from the master secret
		SecretKey inTagPrev = deriveTagKey(master, t, !alice);
		SecretKey inHeaderPrev = deriveHeaderKey(master, t, !alice);
		SecretKey outTagPrev = deriveTagKey(master, t, alice);
		SecretKey outHeaderPrev = deriveHeaderKey(master, t, alice);
		// Derive the keys for the current and next periods
		SecretKey inTagCurr = rotateKey(inTagPrev, rotationPeriod);
		SecretKey inHeaderCurr = rotateKey(inHeaderPrev, rotationPeriod);
		SecretKey inTagNext = rotateKey(inTagCurr, rotationPeriod + 1);
		SecretKey inHeaderNext = rotateKey(inHeaderCurr, rotationPeriod + 1);
		SecretKey outTagCurr = rotateKey(outTagPrev, rotationPeriod);
		SecretKey outHeaderCurr = rotateKey(outHeaderPrev, rotationPeriod);
		// Initialise the reordering windows and stream counters
		IncomingKeys inPrev = new IncomingKeys(inTagPrev, inHeaderPrev,
				rotationPeriod - 1);
		IncomingKeys inCurr = new IncomingKeys(inTagCurr, inHeaderCurr,
				rotationPeriod);
		IncomingKeys inNext = new IncomingKeys(inTagNext, inHeaderNext,
				rotationPeriod + 1);
		OutgoingKeys outCurr = new OutgoingKeys(outTagCurr, outHeaderCurr,
				rotationPeriod);
		// Collect and return the keys
		return new TransportKeys(t, inPrev, inCurr, inNext, outCurr);
	}

	@Override
	public TransportKeys rotateTransportKeys(TransportKeys k,
			long rotationPeriod) {
		if (k.getRotationPeriod() >= rotationPeriod) return k;
		IncomingKeys inPrev = k.getPreviousIncomingKeys();
		IncomingKeys inCurr = k.getCurrentIncomingKeys();
		IncomingKeys inNext = k.getNextIncomingKeys();
		OutgoingKeys outCurr = k.getCurrentOutgoingKeys();
		long startPeriod = outCurr.getRotationPeriod();
		// Rotate the keys
		for (long p = startPeriod + 1; p <= rotationPeriod; p++) {
			inPrev = inCurr;
			inCurr = inNext;
			SecretKey inNextTag = rotateKey(inNext.getTagKey(), p + 1);
			SecretKey inNextHeader = rotateKey(inNext.getHeaderKey(), p + 1);
			inNext = new IncomingKeys(inNextTag, inNextHeader, p + 1);
			SecretKey outCurrTag = rotateKey(outCurr.getTagKey(), p);
			SecretKey outCurrHeader = rotateKey(outCurr.getHeaderKey(), p);
			outCurr = new OutgoingKeys(outCurrTag, outCurrHeader, p);
		}
		// Collect and return the keys
		return new TransportKeys(k.getTransportId(), inPrev, inCurr, inNext,
				outCurr);
	}

	private SecretKey rotateKey(SecretKey k, long rotationPeriod) {
		byte[] period = new byte[INT_64_BYTES];
		ByteUtils.writeUint64(rotationPeriod, period, 0);
		return crypto.deriveKey(ROTATE_LABEL, k, period);
	}

	private SecretKey deriveTagKey(SecretKey master, TransportId t,
			boolean alice) {
		String label = alice ? ALICE_TAG_LABEL : BOB_TAG_LABEL;
		byte[] id = StringUtils.toUtf8(t.getString());
		return crypto.deriveKey(label, master, id);
	}

	private SecretKey deriveHeaderKey(SecretKey master, TransportId t,
			boolean alice) {
		String label = alice ? ALICE_HEADER_LABEL : BOB_HEADER_LABEL;
		byte[] id = StringUtils.toUtf8(t.getString());
		return crypto.deriveKey(label, master, id);
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
		Digest prf = new Blake2sDigest(tagKey.getBytes());
		// The output of the PRF must be long enough to use as a tag
		int macLength = prf.getDigestSize();
		if (macLength < TAG_LENGTH) throw new IllegalStateException();
		// The input is the protocol version as a 16-bit integer, followed by
		// the stream number as a 64-bit integer
		byte[] protocolVersionBytes = new byte[INT_16_BYTES];
		ByteUtils.writeUint16(protocolVersion, protocolVersionBytes, 0);
		prf.update(protocolVersionBytes, 0, protocolVersionBytes.length);
		byte[] streamNumberBytes = new byte[INT_64_BYTES];
		ByteUtils.writeUint64(streamNumber, streamNumberBytes, 0);
		prf.update(streamNumberBytes, 0, streamNumberBytes.length);
		byte[] mac = new byte[macLength];
		prf.doFinal(mac, 0);
		// The output is the first TAG_LENGTH bytes of the MAC
		System.arraycopy(mac, 0, tag, 0, TAG_LENGTH);
	}
}
