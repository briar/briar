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
		w.writeRaw(parent);
		w.writeRaw(group);
		w.writeInt64(timestamp);
		w.writeString(nick);
		w.writeRaw(encodedKey);
		w.writeRaw(body);
		byte[] signable = out.toByteArray();
		signature.initSign(keyPair.getPrivate());
		signature.update(signable);
		byte[] sig = signature.sign();
		w.writeRaw(sig);
		byte[] raw = out.toByteArray();
		w.close();
		// The message ID is the hash of the entire message
		messageDigest.reset();
		messageDigest.update(raw);
		MessageId id = new MessageId(messageDigest.digest());
		// The author ID is the hash of the author's nick and public key
		messageDigest.reset();
		messageDigest.update(nick.getBytes("UTF-8"));
		messageDigest.update((byte) 0); // Null separator
		messageDigest.update(encodedKey);
		AuthorId author = new AuthorId(messageDigest.digest());
		return new MessageImpl(id, parent, group, author, timestamp, raw);
	}
}
