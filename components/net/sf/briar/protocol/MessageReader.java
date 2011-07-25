package net.sf.briar.protocol;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.PublicKey;
import java.security.Signature;
import java.security.SignatureException;

import net.sf.briar.api.crypto.CryptoComponent;
import net.sf.briar.api.crypto.KeyParser;
import net.sf.briar.api.protocol.Author;
import net.sf.briar.api.protocol.AuthorId;
import net.sf.briar.api.protocol.Group;
import net.sf.briar.api.protocol.Message;
import net.sf.briar.api.protocol.MessageId;
import net.sf.briar.api.protocol.Tags;
import net.sf.briar.api.protocol.UniqueId;
import net.sf.briar.api.serial.FormatException;
import net.sf.briar.api.serial.ObjectReader;
import net.sf.briar.api.serial.Reader;

class MessageReader implements ObjectReader<Message> {

	private final ObjectReader<Group> groupReader;
	private final ObjectReader<Author> authorReader;
	private final KeyParser keyParser;
	private final Signature signature;
	private final MessageDigest messageDigest;

	MessageReader(CryptoComponent crypto, ObjectReader<Group> groupReader,
			ObjectReader<Author> authorReader) {
		this.groupReader = groupReader;
		this.authorReader = authorReader;
		keyParser = crypto.getKeyParser();
		signature = crypto.getSignature();
		messageDigest = crypto.getMessageDigest();
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
		byte[] b = r.readBytes();
		if(b.length != UniqueId.LENGTH) throw new FormatException();
		MessageId parent = new MessageId(b);
		// Read the group
		r.addObjectReader(Tags.GROUP, groupReader);
		Group group = r.readUserDefined(Tags.GROUP, Group.class);
		r.removeObjectReader(Tags.GROUP);
		// Read the author, if there is one
		r.addObjectReader(Tags.AUTHOR, authorReader);
		Author author = null;
		if(r.hasNull()) r.readNull();
		else author = r.readUserDefined(Tags.AUTHOR, Author.class);
		r.removeObjectReader(Tags.AUTHOR);
		// Read the timestamp
		long timestamp = r.readInt64();
		if(timestamp < 0L) throw new FormatException();
		// Skip the message body
		r.readBytes();
		// Record the length of the signed data
		int messageLength = (int) counting.getCount();
		// Read the author's signature, if there is one
		byte[] authorSig = null;
		if(author == null) r.readNull();
		else authorSig = r.readBytes();
		// That's all, folks
		r.removeConsumer(counting);
		r.removeConsumer(copying);
		byte[] raw = copying.getCopy();
		// Verify the author's signature, if there is one
		if(author != null) {
			try {
				PublicKey publicKey =
					keyParser.parsePublicKey(author.getPublicKey());
				signature.initVerify(publicKey);
				signature.update(raw, 0, messageLength);
				if(!signature.verify(authorSig)) throw new SignatureException();
			} catch(GeneralSecurityException e) {
				throw new FormatException();
			}
		}
		// Hash the message, including the signature, to get the message ID
		messageDigest.reset();
		messageDigest.update(raw);
		MessageId id = new MessageId(messageDigest.digest());
		AuthorId authorId = author == null ? AuthorId.NONE : author.getId();
		return new MessageImpl(id, parent, group.getId(), authorId, timestamp,
				raw);
	}
}
