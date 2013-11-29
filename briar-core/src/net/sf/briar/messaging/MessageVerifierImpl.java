package net.sf.briar.messaging;

import java.security.GeneralSecurityException;

import javax.inject.Inject;

import net.sf.briar.api.Author;
import net.sf.briar.api.crypto.CryptoComponent;
import net.sf.briar.api.crypto.KeyParser;
import net.sf.briar.api.crypto.MessageDigest;
import net.sf.briar.api.crypto.PublicKey;
import net.sf.briar.api.crypto.Signature;
import net.sf.briar.api.messaging.Message;
import net.sf.briar.api.messaging.MessageId;
import net.sf.briar.api.messaging.MessageVerifier;
import net.sf.briar.api.messaging.UnverifiedMessage;

class MessageVerifierImpl implements MessageVerifier {

	private final CryptoComponent crypto;
	private final KeyParser keyParser;

	@Inject
	MessageVerifierImpl(CryptoComponent crypto) {
		this.crypto = crypto;
		keyParser = crypto.getSignatureKeyParser();
	}

	public Message verifyMessage(UnverifiedMessage m)
			throws GeneralSecurityException {
		MessageDigest messageDigest = crypto.getMessageDigest();
		Signature signature = crypto.getSignature();
		// Hash the message, including the signature, to get the message ID
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
				m.getContentType(), m.getSubject(), m.getTimestamp(), raw,
				m.getBodyStart(), m.getBodyLength());
	}
}
