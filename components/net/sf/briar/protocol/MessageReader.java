package net.sf.briar.protocol;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.PublicKey;
import java.security.Signature;
import java.security.SignatureException;
import java.security.spec.InvalidKeySpecException;

import net.sf.briar.api.crypto.KeyParser;
import net.sf.briar.api.protocol.AuthorId;
import net.sf.briar.api.protocol.GroupId;
import net.sf.briar.api.protocol.Message;
import net.sf.briar.api.protocol.MessageId;
import net.sf.briar.api.protocol.Tags;
import net.sf.briar.api.protocol.UniqueId;
import net.sf.briar.api.serial.FormatException;
import net.sf.briar.api.serial.ObjectReader;
import net.sf.briar.api.serial.Reader;

class MessageReader implements ObjectReader<Message> {

	private final KeyParser keyParser;
	private final Signature signature;
	private final MessageDigest messageDigest;

	MessageReader(KeyParser keyParser, Signature signature,
			MessageDigest messageDigest) {
		this.keyParser = keyParser;
		this.signature = signature;
		this.messageDigest = messageDigest;
	}

	public Message readObject(Reader r) throws IOException {
		CopyingConsumer copying = new CopyingConsumer();
		CountingConsumer counting = new CountingConsumer(Message.MAX_SIZE);
		r.addConsumer(copying);
		r.addConsumer(counting);
		// Read the initial tag
		r.readUserDefinedTag(Tags.MESSAGE);
		// Read the parent's message ID
		r.readUserDefinedTag(Tags.MESSAGE_ID);
		byte[] b = r.readRaw();
		if(b.length != UniqueId.LENGTH) throw new FormatException();
		MessageId parent = new MessageId(b);
		// Read the group ID
		r.readUserDefinedTag(Tags.GROUP_ID);
		b = r.readRaw();
		if(b.length != UniqueId.LENGTH) throw new FormatException();
		GroupId group = new GroupId(b);
		// Read the timestamp
		long timestamp = r.readInt64();
		if(timestamp < 0L) throw new FormatException();
		// Hash the author's nick and public key to get the author ID
		DigestingConsumer digesting = new DigestingConsumer(messageDigest);
		messageDigest.reset();
		r.addConsumer(digesting);
		r.readString();
		byte[] encodedKey = r.readRaw();
		r.removeConsumer(digesting);
		AuthorId author = new AuthorId(messageDigest.digest());
		// Skip the message body
		r.readRaw();
		// Record the length of the signed data
		int messageLength = (int) counting.getCount();
		// Read the signature
		byte[] sig = r.readRaw();
		r.removeConsumer(counting);
		r.removeConsumer(copying);
		// Verify the signature
		PublicKey publicKey;
		try {
			publicKey = keyParser.parsePublicKey(encodedKey);
		} catch(InvalidKeySpecException e) {
			throw new FormatException();
		}
		byte[] raw = copying.getCopy();
		try {
			signature.initVerify(publicKey);
			signature.update(raw, 0, messageLength);
			if(!signature.verify(sig)) throw new SignatureException();
		} catch(GeneralSecurityException e) {
			throw new FormatException();
		}
		// Hash the message, including the signature, to get the message ID
		messageDigest.reset();
		messageDigest.update(raw);
		MessageId id = new MessageId(messageDigest.digest());
		return new MessageImpl(id, parent, group, author, timestamp, raw);
	}
}
