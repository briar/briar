package net.sf.briar.protocol;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.PublicKey;
import java.security.Signature;
import java.security.SignatureException;

import net.sf.briar.api.crypto.KeyParser;
import net.sf.briar.api.protocol.AuthorId;
import net.sf.briar.api.protocol.GroupId;
import net.sf.briar.api.protocol.Message;
import net.sf.briar.api.protocol.MessageId;
import net.sf.briar.api.protocol.MessageParser;
import net.sf.briar.api.protocol.UniqueId;
import net.sf.briar.api.serial.FormatException;
import net.sf.briar.api.serial.Reader;
import net.sf.briar.api.serial.ReaderFactory;

class MessageParserImpl implements MessageParser {

	private final KeyParser keyParser;
	private final Signature signature;
	private final MessageDigest messageDigest;
	private final ReaderFactory readerFactory;

	MessageParserImpl(KeyParser keyParser, Signature signature,
			MessageDigest messageDigest, ReaderFactory readerFactory) {
		this.keyParser = keyParser;
		this.signature = signature;
		this.messageDigest = messageDigest;
		this.readerFactory = readerFactory;
	}

	public Message parseMessage(byte[] raw) throws IOException,
			GeneralSecurityException {
		if(raw.length > Message.MAX_SIZE) throw new FormatException();
		ByteArrayInputStream in = new ByteArrayInputStream(raw);
		Reader r = readerFactory.createReader(in);
		// Read the parent message ID
		byte[] idBytes = r.readRaw();
		if(idBytes.length != UniqueId.LENGTH) throw new FormatException();
		MessageId parent = new MessageId(idBytes);
		// Read the group ID
		idBytes = r.readRaw();
		if(idBytes.length != UniqueId.LENGTH) throw new FormatException();
		GroupId group = new GroupId(idBytes);
		// Read the timestamp
		long timestamp = r.readInt64();
		// Hash the author's nick and public key to get the author ID
		String nick = r.readUtf8();
		byte[] encodedKey = r.readRaw();
		PublicKey publicKey = keyParser.parsePublicKey(encodedKey);
		messageDigest.reset();
		messageDigest.update(nick.getBytes("UTF-8"));
		messageDigest.update((byte) 0); // Null separator
		messageDigest.update(encodedKey);
		AuthorId author = new AuthorId(messageDigest.digest());
		// Skip the message body
		r.readRaw();
		// Read the signature and work out how long the signed message is
		byte[] sig = r.readRaw();
		int length = raw.length - sig.length - bytesToEncode(sig.length);
		// Verify the signature
		signature.initVerify(publicKey);
		signature.update(raw, 0, length);
		if(!signature.verify(sig)) throw new SignatureException();
		// Hash the message, including the signature, to get the message ID
		messageDigest.reset();
		messageDigest.update(raw);
		MessageId id = new MessageId(messageDigest.digest());
		return new MessageImpl(id, parent, group, author, timestamp, raw);
	}

	// FIXME: Work out a better way of doing this
	private int bytesToEncode(int i) {
		if(i >= 0 && i <= Byte.MAX_VALUE) return 2;
		if(i >= Byte.MIN_VALUE && i <= Byte.MAX_VALUE) return 3;
		if(i >= Short.MIN_VALUE && i <= Short.MAX_VALUE) return 4;
		return 6;
	}
}
