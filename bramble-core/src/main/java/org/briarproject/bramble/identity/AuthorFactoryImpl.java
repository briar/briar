package org.briarproject.bramble.identity;

import org.briarproject.bramble.api.crypto.CryptoComponent;
import org.briarproject.bramble.api.crypto.KeyPair;
import org.briarproject.bramble.api.identity.Author;
import org.briarproject.bramble.api.identity.AuthorFactory;
import org.briarproject.bramble.api.identity.AuthorId;
import org.briarproject.bramble.api.identity.LocalAuthor;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.system.Clock;
import org.briarproject.bramble.util.ByteUtils;
import org.briarproject.bramble.util.StringUtils;

import javax.annotation.concurrent.Immutable;
import javax.inject.Inject;

import static org.briarproject.bramble.api.identity.Author.FORMAT_VERSION;
import static org.briarproject.bramble.api.identity.AuthorId.LABEL;
import static org.briarproject.bramble.util.ByteUtils.INT_32_BYTES;

@Immutable
@NotNullByDefault
class AuthorFactoryImpl implements AuthorFactory {

	private final CryptoComponent crypto;
	private final Clock clock;

	@Inject
	AuthorFactoryImpl(CryptoComponent crypto, Clock clock) {
		this.crypto = crypto;
		this.clock = clock;
	}

	@Override
	public Author createAuthor(String name, byte[] publicKey) {
		return createAuthor(FORMAT_VERSION, name, publicKey);
	}

	@Override
	public Author createAuthor(int formatVersion, String name,
			byte[] publicKey) {
		AuthorId id = getId(formatVersion, name, publicKey);
		return new Author(id, formatVersion, name, publicKey);
	}

	@Override
	public LocalAuthor createLocalAuthor(String name, boolean handshakeKeys) {
		KeyPair signatureKeyPair = crypto.generateSignatureKeyPair();
		byte[] sigPub = signatureKeyPair.getPublic().getEncoded();
		byte[] sigPriv = signatureKeyPair.getPrivate().getEncoded();
		AuthorId id = getId(FORMAT_VERSION, name, sigPub);
		if (handshakeKeys) {
			KeyPair handshakeKeyPair = crypto.generateAgreementKeyPair();
			byte[] handPub = handshakeKeyPair.getPublic().getEncoded();
			byte[] handPriv = handshakeKeyPair.getPrivate().getEncoded();
			return new LocalAuthor(id, FORMAT_VERSION, name, sigPub, sigPriv,
					handPub, handPriv, clock.currentTimeMillis());
		} else {
			return new LocalAuthor(id, FORMAT_VERSION, name, sigPub, sigPriv,
					clock.currentTimeMillis());
		}
	}

	private AuthorId getId(int formatVersion, String name, byte[] publicKey) {
		byte[] formatVersionBytes = new byte[INT_32_BYTES];
		ByteUtils.writeUint32(formatVersion, formatVersionBytes, 0);
		return new AuthorId(crypto.hash(LABEL, formatVersionBytes,
				StringUtils.toUtf8(name), publicKey));
	}
}
