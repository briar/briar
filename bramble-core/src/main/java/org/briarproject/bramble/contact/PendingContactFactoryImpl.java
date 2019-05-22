package org.briarproject.bramble.contact;

import org.briarproject.bramble.api.FormatException;
import org.briarproject.bramble.api.UnsupportedVersionException;
import org.briarproject.bramble.api.contact.PendingContact;
import org.briarproject.bramble.api.contact.PendingContactId;
import org.briarproject.bramble.api.crypto.CryptoComponent;
import org.briarproject.bramble.api.crypto.KeyParser;
import org.briarproject.bramble.api.crypto.PublicKey;
import org.briarproject.bramble.api.system.Clock;
import org.briarproject.bramble.util.Base32;

import java.security.GeneralSecurityException;
import java.util.regex.Matcher;

import javax.inject.Inject;

import static java.lang.System.arraycopy;
import static org.briarproject.bramble.api.contact.HandshakeLinkConstants.FORMAT_VERSION;
import static org.briarproject.bramble.api.contact.HandshakeLinkConstants.ID_LABEL;
import static org.briarproject.bramble.api.contact.HandshakeLinkConstants.LINK_REGEX;
import static org.briarproject.bramble.api.contact.HandshakeLinkConstants.RAW_LINK_BYTES;

class PendingContactFactoryImpl implements PendingContactFactory {

	private final CryptoComponent crypto;
	private final Clock clock;

	@Inject
	PendingContactFactoryImpl(CryptoComponent crypto, Clock clock) {
		this.crypto = crypto;
		this.clock = clock;
	}

	@Override
	public PendingContact createPendingContact(String link, String alias)
			throws FormatException {
		PublicKey publicKey = parseHandshakeLink(link);
		PendingContactId id = getPendingContactId(publicKey);
		long timestamp = clock.currentTimeMillis();
		return new PendingContact(id, publicKey, alias, timestamp);
	}

	private PublicKey parseHandshakeLink(String link) throws FormatException {
		Matcher matcher = LINK_REGEX.matcher(link);
		if (!matcher.find()) throw new FormatException();
		// Discard 'briar://' and anything before or after the link
		link = matcher.group(2);
		byte[] base32 = Base32.decode(link, false);
		if (base32.length != RAW_LINK_BYTES) throw new AssertionError();
		byte version = base32[0];
		if (version != FORMAT_VERSION)
			throw new UnsupportedVersionException(version < FORMAT_VERSION);
		byte[] publicKeyBytes = new byte[base32.length - 1];
		arraycopy(base32, 1, publicKeyBytes, 0, publicKeyBytes.length);
		try {
			KeyParser parser = crypto.getAgreementKeyParser();
			return parser.parsePublicKey(publicKeyBytes);
		} catch (GeneralSecurityException e) {
			throw new FormatException();
		}
	}

	private PendingContactId getPendingContactId(PublicKey publicKey) {
		byte[] hash = crypto.hash(ID_LABEL, publicKey.getEncoded());
		return new PendingContactId(hash);
	}
}
