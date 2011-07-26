package net.sf.briar.protocol;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.PublicKey;
import java.security.Signature;

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

import com.google.inject.Inject;

class MessageReader implements ObjectReader<Message> {

	private final ObjectReader<Group> groupReader;
	private final ObjectReader<Author> authorReader;
	private final KeyParser keyParser;
	private final Signature signature;
	private final MessageDigest messageDigest;

	@Inject
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
		// Record the length of the data covered by the author's signature
		int signedByAuthor = (int) counting.getCount();
		// Read the author's signature, if there is one
		byte[] authorSig = null;
		if(author == null) r.readNull();
		else authorSig = r.readBytes();
		// Record the length of the data covered by the group's signature
		int signedByGroup = (int) counting.getCount();
		// Read the group's signature, if there is one
		byte[] groupSig = null;
		if(group.getPublicKey() == null) r.readNull();
		else groupSig = r.readBytes();
		// That's all, folks
		r.removeConsumer(counting);
		r.removeConsumer(copying);
		byte[] raw = copying.getCopy();
		// Verify the author's signature, if there is one
		if(author != null) {
			try {
				PublicKey k = keyParser.parsePublicKey(author.getPublicKey());
				signature.initVerify(k);
				signature.update(raw, 0, signedByAuthor);
				if(!signature.verify(authorSig)) throw new FormatException();
			} catch(GeneralSecurityException e) {
				throw new FormatException();
			}
		}
		// Verify the group's signature, if there is one
		if(group.getPublicKey() != null) {
			try {
				PublicKey k = keyParser.parsePublicKey(group.getPublicKey());
				signature.initVerify(k);
				signature.update(raw, 0, signedByGroup);
				if(!signature.verify(groupSig)) throw new FormatException();
			} catch(GeneralSecurityException e) {
				throw new FormatException();
			}
		}
		// Hash the message, including the signatures, to get the message ID
		messageDigest.reset();
		messageDigest.update(raw);
		MessageId id = new MessageId(messageDigest.digest());
		AuthorId authorId = author == null ? AuthorId.NONE : author.getId();
		return new MessageImpl(id, parent, group.getId(), authorId, timestamp,
				raw);
	}
}
