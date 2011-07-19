package net.sf.briar.protocol;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.MessageDigest;
import java.security.Signature;

import net.sf.briar.api.protocol.AuthorId;
import net.sf.briar.api.protocol.GroupId;
import net.sf.briar.api.protocol.Message;
import net.sf.briar.api.protocol.MessageEncoder;
import net.sf.briar.api.protocol.MessageId;
import net.sf.briar.api.protocol.Tags;
import net.sf.briar.api.serial.Writer;
import net.sf.briar.api.serial.WriterFactory;

class MessageEncoderImpl implements MessageEncoder {

	private final Signature signature;
	private final MessageDigest messageDigest;
	private final WriterFactory writerFactory;

	MessageEncoderImpl(Signature signature, MessageDigest messageDigest,
			WriterFactory writerFactory) {
		this.signature = signature;
		this.messageDigest = messageDigest;
		this.writerFactory = writerFactory;
	}

	public Message encodeMessage(MessageId parent, GroupId group, String nick,
			KeyPair keyPair, byte[] body) throws IOException,
			GeneralSecurityException {
		long timestamp = System.currentTimeMillis();
		byte[] encodedKey = keyPair.getPublic().getEncoded();
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		Writer w = writerFactory.createWriter(out);
		parent.writeTo(w);
		group.writeTo(w);
		w.writeUserDefinedTag(Tags.TIMESTAMP);
		w.writeInt64(timestamp);
		w.writeUserDefinedTag(Tags.AUTHOR);
		w.writeString(nick);
		w.writeRaw(encodedKey);
		w.writeUserDefinedTag(Tags.MESSAGE_BODY);
		w.writeRaw(body);
		byte[] signable = out.toByteArray();
		signature.initSign(keyPair.getPrivate());
		signature.update(signable);
		byte[] sig = signature.sign();
		w.writeUserDefinedTag(Tags.SIGNATURE);
		w.writeRaw(sig);
		byte[] raw = out.toByteArray();
		w.close();
		// The message ID is the hash of the entire message
		messageDigest.reset();
		messageDigest.update(raw);
		MessageId id = new MessageId(messageDigest.digest());
		// The author ID is the hash of the author's nick and public key
		out.reset();
		w = writerFactory.createWriter(out);
		w.writeString(nick);
		w.writeRaw(encodedKey);
		w.close();
		messageDigest.reset();
		messageDigest.update(out.toByteArray());
		AuthorId author = new AuthorId(messageDigest.digest());
		return new MessageImpl(id, parent, group, author, timestamp, raw);
	}
}
