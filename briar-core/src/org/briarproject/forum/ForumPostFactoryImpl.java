package org.briarproject.forum;

import org.briarproject.api.crypto.CryptoComponent;
import org.briarproject.api.crypto.PrivateKey;
import org.briarproject.api.crypto.Signature;
import org.briarproject.api.data.BdfWriter;
import org.briarproject.api.data.BdfWriterFactory;
import org.briarproject.api.forum.ForumPost;
import org.briarproject.api.forum.ForumPostFactory;
import org.briarproject.api.identity.Author;
import org.briarproject.api.sync.GroupId;
import org.briarproject.api.sync.Message;
import org.briarproject.api.sync.MessageFactory;
import org.briarproject.api.sync.MessageId;
import org.briarproject.util.StringUtils;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.security.GeneralSecurityException;

import javax.inject.Inject;

import static org.briarproject.api.forum.ForumConstants.MAX_CONTENT_TYPE_LENGTH;
import static org.briarproject.api.forum.ForumConstants.MAX_FORUM_POST_BODY_LENGTH;

class ForumPostFactoryImpl implements ForumPostFactory {

	private final CryptoComponent crypto;
	private final MessageFactory messageFactory;
	private final BdfWriterFactory bdfWriterFactory;

	@Inject
	ForumPostFactoryImpl(CryptoComponent crypto, MessageFactory messageFactory,
			BdfWriterFactory bdfWriterFactory) {
		this.crypto = crypto;
		this.messageFactory = messageFactory;
		this.bdfWriterFactory = bdfWriterFactory;
	}

	@Override
	public ForumPost createAnonymousPost(GroupId groupId, long timestamp,
			MessageId parent, String contentType, byte[] body)
			throws IOException, GeneralSecurityException {
		// Validate the arguments
		if (StringUtils.toUtf8(contentType).length > MAX_CONTENT_TYPE_LENGTH)
			throw new IllegalArgumentException();
		if (body.length > MAX_FORUM_POST_BODY_LENGTH)
			throw new IllegalArgumentException();
		// Serialise the message to a buffer
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		BdfWriter w = bdfWriterFactory.createWriter(out);
		w.writeListStart();
		if (parent == null) w.writeNull();
		else w.writeRaw(parent.getBytes());
		w.writeNull(); // No author
		w.writeString(contentType);
		w.writeRaw(body);
		w.writeNull(); // No signature
		w.writeListEnd();
		Message m = messageFactory.createMessage(groupId, timestamp,
				out.toByteArray());
		return new ForumPost(m, parent, null, contentType);
	}

	@Override
	public ForumPost createPseudonymousPost(GroupId groupId, long timestamp,
			MessageId parent, Author author, String contentType, byte[] body,
			PrivateKey privateKey) throws IOException,
			GeneralSecurityException {
		// Validate the arguments
		if (StringUtils.toUtf8(contentType).length > MAX_CONTENT_TYPE_LENGTH)
			throw new IllegalArgumentException();
		if (body.length > MAX_FORUM_POST_BODY_LENGTH)
			throw new IllegalArgumentException();
		// Serialise the data to be signed
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		BdfWriter w = bdfWriterFactory.createWriter(out);
		w.writeListStart();
		w.writeRaw(groupId.getBytes());
		w.writeLong(timestamp);
		if (parent == null) w.writeNull();
		else w.writeRaw(parent.getBytes());
		writeAuthor(w, author);
		w.writeString(contentType);
		w.writeRaw(body);
		w.writeListEnd();
		// Generate the signature
		Signature signature = crypto.getSignature();
		signature.initSign(privateKey);
		signature.update(out.toByteArray());
		byte[] sig = signature.sign();
		// Serialise the signed message
		out.reset();
		w = bdfWriterFactory.createWriter(out);
		w.writeListStart();
		if (parent == null) w.writeNull();
		else w.writeRaw(parent.getBytes());
		writeAuthor(w, author);
		w.writeString(contentType);
		w.writeRaw(body);
		w.writeRaw(sig);
		w.writeListEnd();
		Message m = messageFactory.createMessage(groupId, timestamp,
				out.toByteArray());
		return new ForumPost(m, parent, author, contentType);
	}

	private void writeAuthor(BdfWriter w, Author a) throws IOException {
		w.writeListStart();
		w.writeString(a.getName());
		w.writeRaw(a.getPublicKey());
		w.writeListEnd();
	}
}
