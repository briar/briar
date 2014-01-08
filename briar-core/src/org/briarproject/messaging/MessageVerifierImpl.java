package org.briarproject.messaging;

import static org.briarproject.api.transport.TransportConstants.MAX_CLOCK_DIFFERENCE;

import java.security.GeneralSecurityException;

import javax.inject.Inject;

import org.briarproject.api.Author;
import org.briarproject.api.crypto.CryptoComponent;
import org.briarproject.api.crypto.KeyParser;
import org.briarproject.api.crypto.MessageDigest;
import org.briarproject.api.crypto.PublicKey;
import org.briarproject.api.crypto.Signature;
import org.briarproject.api.messaging.Message;
import org.briarproject.api.messaging.MessageId;
import org.briarproject.api.messaging.MessageVerifier;
import org.briarproject.api.messaging.UnverifiedMessage;
import org.briarproject.api.system.Clock;

class MessageVerifierImpl implements MessageVerifier {

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
		MessageDigest messageDigest = crypto.getMessageDigest();
		Signature signature = crypto.getSignature();
		// Reject the message if it's too far in the future
		long now = clock.currentTimeMillis();
		if(m.getTimestamp() > now + MAX_CLOCK_DIFFERENCE)
			throw new GeneralSecurityException();
		// Hash the message to get the message ID
		byte[] raw = m.getSerialised();
		messageDigest.update(raw);
		MessageId id = new MessageId(messageDigest.digest());
		// Verify the author's signature, if there is one
		Author author = m.getAuthor();
		if(author != null) {
			PublicKey k = keyParser.parsePublicKey(author.getPublicKey());
			signature.initVerify(k);
			signature.update(raw, 0, m.getSignedLength());
			if(!signature.verify(m.getSignature()))
				throw new GeneralSecurityException();
		}
		return new MessageImpl(id, m.getParent(), m.getGroup(), author,
				m.getContentType(), m.getTimestamp(), raw, m.getBodyStart(),
				m.getBodyLength());
	}
}
