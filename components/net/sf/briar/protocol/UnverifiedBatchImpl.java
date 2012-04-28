package net.sf.briar.protocol;

import java.security.GeneralSecurityException;
import java.security.PublicKey;
import java.security.Signature;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import net.sf.briar.api.crypto.CryptoComponent;
import net.sf.briar.api.crypto.KeyParser;
import net.sf.briar.api.crypto.MessageDigest;
import net.sf.briar.api.protocol.Author;
import net.sf.briar.api.protocol.AuthorId;
import net.sf.briar.api.protocol.Batch;
import net.sf.briar.api.protocol.BatchId;
import net.sf.briar.api.protocol.Group;
import net.sf.briar.api.protocol.GroupId;
import net.sf.briar.api.protocol.Message;
import net.sf.briar.api.protocol.MessageId;
import net.sf.briar.api.protocol.UnverifiedBatch;

class UnverifiedBatchImpl implements UnverifiedBatch {

	private final CryptoComponent crypto;
	private final Collection<UnverifiedMessage> messages;
	private final MessageDigest batchDigest, messageDigest;

	// Initialise lazily - the batch may contain unsigned messages
	private KeyParser keyParser = null;
	private Signature signature = null;

	UnverifiedBatchImpl(CryptoComponent crypto,
			Collection<UnverifiedMessage> messages) {
		this.crypto = crypto;
		this.messages = messages;
		batchDigest = crypto.getMessageDigest();
		messageDigest = crypto.getMessageDigest();
	}

	public Batch verify() throws GeneralSecurityException {
		List<Message> verified = new ArrayList<Message>();
		for(UnverifiedMessage m : messages) verified.add(verify(m));
		BatchId id = new BatchId(batchDigest.digest());
		return new BatchImpl(id, Collections.unmodifiableList(verified));
	}

	private Message verify(UnverifiedMessage m)
	throws GeneralSecurityException {
		// The batch ID is the hash of the concatenated messages
		byte[] raw = m.getRaw();
		batchDigest.update(raw);
		// Hash the message, including the signatures, to get the message ID
		messageDigest.update(raw);
		MessageId id = new MessageId(messageDigest.digest());
		// Verify the author's signature, if there is one
		Author author = m.getAuthor();
		if(author != null) {
			if(keyParser == null) keyParser = crypto.getSignatureKeyParser();
			PublicKey k = keyParser.parsePublicKey(author.getPublicKey());
			if(signature == null) signature = crypto.getSignature();
			signature.initVerify(k);
			signature.update(raw, 0, m.getLengthSignedByAuthor());
			if(!signature.verify(m.getAuthorSignature()))
				throw new GeneralSecurityException();
		}
		// Verify the group's signature, if there is one
		Group group = m.getGroup();
		if(group != null && group.getPublicKey() != null) {
			if(keyParser == null) keyParser = crypto.getSignatureKeyParser();
			PublicKey k = keyParser.parsePublicKey(group.getPublicKey());
			if(signature == null) signature = crypto.getSignature();
			signature.initVerify(k);
			signature.update(raw, 0, m.getLengthSignedByGroup());
			if(!signature.verify(m.getGroupSignature()))
				throw new GeneralSecurityException();
		}
		GroupId groupId = group == null ? null : group.getId();
		AuthorId authorId = author == null ? null : author.getId();
		return new MessageImpl(id, m.getParent(), groupId, authorId,
				m.getSubject(), m.getTimestamp(), raw, m.getBodyStart(),
				m.getBodyLength());
	}
}
