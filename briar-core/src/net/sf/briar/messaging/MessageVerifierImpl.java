package net.sf.briar.messaging;

import java.security.GeneralSecurityException;

import net.sf.briar.api.Author;
import net.sf.briar.api.crypto.CryptoComponent;
import net.sf.briar.api.crypto.KeyParser;
import net.sf.briar.api.crypto.MessageDigest;
import net.sf.briar.api.crypto.PublicKey;
import net.sf.briar.api.crypto.Signature;
import net.sf.briar.api.messaging.Group;
import net.sf.briar.api.messaging.Message;
import net.sf.briar.api.messaging.MessageId;
import net.sf.briar.api.messaging.MessageVerifier;
import net.sf.briar.api.messaging.UnverifiedMessage;

import com.google.inject.Inject;

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
		// Hash the message, including the signatures, to get the message ID
		byte[] raw = m.getSerialised();
		messageDigest.update(raw);
		MessageId id = new MessageId(messageDigest.digest());
		// Verify the author's signature, if there is one
		Author author = m.getAuthor();
		if(author != null) {
			PublicKey k = keyParser.parsePublicKey(author.getPublicKey());
			signature.initVerify(k);
			signature.update(raw, 0, m.getLengthSignedByAuthor());
			if(!signature.verify(m.getAuthorSignature()))
				throw new GeneralSecurityException();
		}
		// Verify the group's signature, if there is one
		Group group = m.getGroup();
		if(group != null && group.isRestricted()) {
			PublicKey k = keyParser.parsePublicKey(group.getPublicKey());
			signature.initVerify(k);
			signature.update(raw, 0, m.getLengthSignedByGroup());
			if(!signature.verify(m.getGroupSignature()))
				throw new GeneralSecurityException();
		}
		return new MessageImpl(id, m.getParent(), group, author,
				m.getContentType(), m.getSubject(), m.getTimestamp(), raw,
				m.getBodyStart(), m.getBodyLength());
	}
}
