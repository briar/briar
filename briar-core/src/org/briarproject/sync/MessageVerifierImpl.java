package org.briarproject.sync;

import org.briarproject.api.Author;
import org.briarproject.api.crypto.CryptoComponent;
import org.briarproject.api.crypto.KeyParser;
import org.briarproject.api.crypto.MessageDigest;
import org.briarproject.api.crypto.PublicKey;
import org.briarproject.api.crypto.Signature;
import org.briarproject.api.sync.Message;
import org.briarproject.api.sync.MessageId;
import org.briarproject.api.sync.MessageVerifier;
import org.briarproject.api.sync.UnverifiedMessage;
import org.briarproject.api.system.Clock;

import java.security.GeneralSecurityException;
import java.util.logging.Logger;

import javax.inject.Inject;

import static java.util.logging.Level.INFO;
import static org.briarproject.api.transport.TransportConstants.MAX_CLOCK_DIFFERENCE;

class MessageVerifierImpl implements MessageVerifier {

	private static final Logger LOG =
			Logger.getLogger(MessageVerifierImpl.class.getName());

	private final CryptoComponent crypto;
	private final Clock clock;
	private final KeyParser keyParser;

	@Inject
	MessageVerifierImpl(CryptoComponent crypto, Clock clock) {
		this.crypto = crypto;
		this.clock = clock;
		keyParser = crypto.getSignatureKeyParser();
	}

	public Message verifyMessage(UnverifiedMessage m)
			throws GeneralSecurityException {
		long now = System.currentTimeMillis();
		MessageDigest messageDigest = crypto.getMessageDigest();
		Signature signature = crypto.getSignature();
		// Reject the message if it's too far in the future
		if (m.getTimestamp() > clock.currentTimeMillis() + MAX_CLOCK_DIFFERENCE)
			throw new GeneralSecurityException();
		// Hash the message to get the message ID
		byte[] raw = m.getSerialised();
		messageDigest.update(raw);
		MessageId id = new MessageId(messageDigest.digest());
		// Verify the author's signature, if there is one
		Author author = m.getAuthor();
		if (author != null) {
			PublicKey k = keyParser.parsePublicKey(author.getPublicKey());
			signature.initVerify(k);
			signature.update(raw, 0, m.getSignedLength());
			if (!signature.verify(m.getSignature()))
				throw new GeneralSecurityException();
		}
		Message verified = new MessageImpl(id, m.getParent(), m.getGroup(),
				author, m.getContentType(), m.getTimestamp(), raw,
				m.getBodyStart(), m.getBodyLength());
		long duration = System.currentTimeMillis() - now;
		if (LOG.isLoggable(INFO))
			LOG.info("Verifying message took " + duration + " ms");
		return verified;
	}
}
