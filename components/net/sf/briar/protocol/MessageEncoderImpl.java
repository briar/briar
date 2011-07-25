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
	throws IOException {
		long timestamp = System.currentTimeMillis();
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		Writer w = writerFactory.createWriter(out);
		// Write the message
		w.writeUserDefinedTag(Tags.MESSAGE);
		parent.writeTo(w);
		group.writeTo(w);
		w.writeNull(); // No author
		w.writeInt64(timestamp);
		w.writeBytes(body);
		w.writeNull(); // No author's signature
		byte[] raw = out.toByteArray();
		// The message ID is the hash of the entire message
		messageDigest.reset();
		messageDigest.update(raw);
		MessageId id = new MessageId(messageDigest.digest());
		return new MessageImpl(id, parent, group.getId(), AuthorId.NONE,
				timestamp, raw);
	}

	public Message encodeMessage(MessageId parent, Group group, Author author,
			PrivateKey privateKey, byte[] body) throws IOException,
			GeneralSecurityException {
		long timestamp = System.currentTimeMillis();
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		Writer w = writerFactory.createWriter(out);
		// Write the message
		w.writeUserDefinedTag(Tags.MESSAGE);
		parent.writeTo(w);
		group.writeTo(w);
		author.writeTo(w);
		w.writeInt64(timestamp);
		w.writeBytes(body);
		// Sign the message
		byte[] signable = out.toByteArray();
		signature.initSign(privateKey);
		signature.update(signable);
		byte[] sig = signature.sign();
		signable = null;
		// Write the signature
		w.writeBytes(sig);
		byte[] raw = out.toByteArray();
		// The message ID is the hash of the entire message
		messageDigest.reset();
		messageDigest.update(raw);
		MessageId id = new MessageId(messageDigest.digest());
		// The author ID is the hash of the author object
		out.reset();
		w = writerFactory.createWriter(out);
		author.writeTo(w);
		messageDigest.reset();
		messageDigest.update(out.toByteArray());
		AuthorId authorId = new AuthorId(messageDigest.digest());
		return new MessageImpl(id, parent, group.getId(), authorId, timestamp,
				raw);
	}
}
