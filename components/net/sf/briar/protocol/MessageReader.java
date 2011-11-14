package net.sf.briar.protocol;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.PublicKey;
import java.security.Signature;

import net.sf.briar.api.FormatException;
import net.sf.briar.api.crypto.CryptoComponent;
import net.sf.briar.api.crypto.KeyParser;
import net.sf.briar.api.protocol.Author;
import net.sf.briar.api.protocol.AuthorId;
import net.sf.briar.api.protocol.Group;
import net.sf.briar.api.protocol.GroupId;
import net.sf.briar.api.protocol.Message;
import net.sf.briar.api.protocol.MessageId;
import net.sf.briar.api.protocol.ProtocolConstants;
import net.sf.briar.api.protocol.Types;
import net.sf.briar.api.serial.ObjectReader;
import net.sf.briar.api.serial.Reader;

class MessageReader implements ObjectReader<Message> {

	private final ObjectReader<MessageId> messageIdReader;
	private final ObjectReader<Group> groupReader;
	private final ObjectReader<Author> authorReader;
	private final KeyParser keyParser;
	private final Signature signature;
	private final MessageDigest messageDigest;

	MessageReader(CryptoComponent crypto,
			ObjectReader<MessageId> messageIdReader,
			ObjectReader<Group> groupReader,
			ObjectReader<Author> authorReader) {
		this.messageIdReader = messageIdReader;
		this.groupReader = groupReader;
		this.authorReader = authorReader;
		keyParser = crypto.getKeyParser();
		signature = crypto.getSignature();
		messageDigest = crypto.getMessageDigest();
	}

	public Message readObject(Reader r) throws IOException {
		CopyingConsumer copying = new CopyingConsumer();
		CountingConsumer counting =
			new CountingConsumer(ProtocolConstants.MAX_PACKET_LENGTH);
		r.addConsumer(copying);
		r.addConsumer(counting);
		// Read the initial tag
		r.readUserDefinedId(Types.MESSAGE);
		// Read the parent's message ID, if there is one
		MessageId parent = null;
		if(r.hasNull()) {
			r.readNull();
		} else {
			r.addObjectReader(Types.MESSAGE_ID, messageIdReader);
			parent = r.readUserDefined(Types.MESSAGE_ID, MessageId.class);
			r.removeObjectReader(Types.MESSAGE_ID);
		}
		// Read the group, if there is one
		Group group = null;
		if(r.hasNull()) {
			r.readNull();
		} else {
			r.addObjectReader(Types.GROUP, groupReader);
			group = r.readUserDefined(Types.GROUP, Group.class);
			r.removeObjectReader(Types.GROUP);
		}
		// Read the author, if there is one
		Author author = null;
		if(r.hasNull()) {
			r.readNull();
		} else {
			r.addObjectReader(Types.AUTHOR, authorReader);
			author = r.readUserDefined(Types.AUTHOR, Author.class);
			r.removeObjectReader(Types.AUTHOR);
		}
		// Read the subject
		String subject = r.readString(ProtocolConstants.MAX_SUBJECT_LENGTH);
		// Read the timestamp
		long timestamp = r.readInt64();
		if(timestamp < 0L) throw new FormatException();
		// Read the salt
		byte[] salt = r.readBytes(ProtocolConstants.SALT_LENGTH);
		if(salt.length != ProtocolConstants.SALT_LENGTH) throw new FormatException();
		// Read the message body
		byte[] body = r.readBytes(ProtocolConstants.MAX_BODY_LENGTH);
		// Record the offset of the body within the message
		int bodyStart = (int) counting.getCount() - body.length;
		// Record the length of the data covered by the author's signature
		int signedByAuthor = (int) counting.getCount();
		// Read the author's signature, if there is one
		byte[] authorSig = null;
		if(author == null) r.readNull();
		else authorSig = r.readBytes(ProtocolConstants.MAX_SIGNATURE_LENGTH);
		// Record the length of the data covered by the group's signature
		int signedByGroup = (int) counting.getCount();
		// Read the group's signature, if there is one
		byte[] groupSig = null;
		if(group == null || group.getPublicKey() == null) r.readNull();
		else groupSig = r.readBytes(ProtocolConstants.MAX_SIGNATURE_LENGTH);
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
		if(group != null && group.getPublicKey() != null) {
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
		GroupId groupId = group == null ? null : group.getId();
		AuthorId authorId = author == null ? null : author.getId();
		return new MessageImpl(id, parent, groupId, authorId, subject,
				timestamp, raw, bodyStart, body.length);
	}
}
