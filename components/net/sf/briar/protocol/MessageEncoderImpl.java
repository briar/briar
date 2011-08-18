package net.sf.briar.protocol;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.Signature;

import net.sf.briar.api.crypto.CryptoComponent;
import net.sf.briar.api.protocol.Author;
import net.sf.briar.api.protocol.AuthorId;
import net.sf.briar.api.protocol.Group;
import net.sf.briar.api.protocol.Message;
import net.sf.briar.api.protocol.MessageEncoder;
import net.sf.briar.api.protocol.MessageId;
import net.sf.briar.api.protocol.Tags;
import net.sf.briar.api.serial.Writer;
import net.sf.briar.api.serial.WriterFactory;

import com.google.inject.Inject;

class MessageEncoderImpl implements MessageEncoder {

	private final Signature signature;
	private final MessageDigest messageDigest;
	private final WriterFactory writerFactory;

	@Inject
	MessageEncoderImpl(CryptoComponent crypto, WriterFactory writerFactory) {
		signature = crypto.getSignature();
		messageDigest = crypto.getMessageDigest();
		this.writerFactory = writerFactory;
	}

	public Message encodeMessage(MessageId parent, Group group, byte[] body)
	throws IOException, GeneralSecurityException {
		return encodeMessage(parent, group, null, null, null, body);
	}

	public Message encodeMessage(MessageId parent, Group group,
			PrivateKey groupKey, byte[] body) throws IOException,
			GeneralSecurityException {
		return encodeMessage(parent, group, groupKey, null, null, body);
	}

	public Message encodeMessage(MessageId parent, Group group, Author author,
			PrivateKey authorKey, byte[] body) throws IOException,
			GeneralSecurityException {
		return encodeMessage(parent, group, null, author, authorKey, body);
	}

	public Message encodeMessage(MessageId parent, Group group,
			PrivateKey groupKey, Author author, PrivateKey authorKey,
			byte[] body) throws IOException, GeneralSecurityException {

		if((author == null) != (authorKey == null))
			throw new IllegalArgumentException();
		if((group.getPublicKey() == null) != (groupKey == null))
			throw new IllegalArgumentException();
		if(body.length > Message.MAX_BODY_LENGTH)
			throw new IllegalArgumentException();

		long timestamp = System.currentTimeMillis();
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		Writer w = writerFactory.createWriter(out);
		// Write the message
		w.writeUserDefinedTag(Tags.MESSAGE);
		parent.writeTo(w);
		group.writeTo(w);
		if(author == null) w.writeNull();
		else author.writeTo(w);
		w.writeInt64(timestamp);
		w.writeBytes(body);
		// Sign the message with the author's private key, if there is one
		if(authorKey == null) {
			w.writeNull();
		} else {
			signature.initSign(authorKey);
			signature.update(out.toByteArray());
			w.writeBytes(signature.sign());
		}
		// Sign the message with the group's private key, if there is one
		if(groupKey == null) {
			w.writeNull();
		} else {
			signature.initSign(groupKey);
			signature.update(out.toByteArray());
			w.writeBytes(signature.sign());
		}
		// Hash the message, including the signatures, to get the message ID
		byte[] raw = out.toByteArray();
		messageDigest.reset();
		messageDigest.update(raw);
		MessageId id = new MessageId(messageDigest.digest());
		AuthorId authorId = author == null ? AuthorId.NONE : author.getId();
		return new MessageImpl(id, parent, group.getId(), authorId, timestamp,
				raw);
	}
}
