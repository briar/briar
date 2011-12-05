package net.sf.briar.protocol;

import java.io.IOException;

import net.sf.briar.api.FormatException;
import net.sf.briar.api.protocol.Author;
import net.sf.briar.api.protocol.Group;
import net.sf.briar.api.protocol.MessageId;
import net.sf.briar.api.protocol.ProtocolConstants;
import net.sf.briar.api.protocol.Types;
import net.sf.briar.api.serial.CopyingConsumer;
import net.sf.briar.api.serial.CountingConsumer;
import net.sf.briar.api.serial.ObjectReader;
import net.sf.briar.api.serial.Reader;

class MessageReader implements ObjectReader<UnverifiedMessage> {

	private final ObjectReader<MessageId> messageIdReader;
	private final ObjectReader<Group> groupReader;
	private final ObjectReader<Author> authorReader;

	MessageReader(ObjectReader<MessageId> messageIdReader,
			ObjectReader<Group> groupReader,
			ObjectReader<Author> authorReader) {
		this.messageIdReader = messageIdReader;
		this.groupReader = groupReader;
		this.authorReader = authorReader;
	}

	public UnverifiedMessage readObject(Reader r) throws IOException {
		CopyingConsumer copying = new CopyingConsumer();
		CountingConsumer counting =
			new CountingConsumer(ProtocolConstants.MAX_PACKET_LENGTH);
		r.addConsumer(copying);
		r.addConsumer(counting);
		// Read the initial tag
		r.readStructId(Types.MESSAGE);
		// Read the parent's message ID, if there is one
		MessageId parent = null;
		if(r.hasNull()) {
			r.readNull();
		} else {
			r.addObjectReader(Types.MESSAGE_ID, messageIdReader);
			parent = r.readStruct(Types.MESSAGE_ID, MessageId.class);
			r.removeObjectReader(Types.MESSAGE_ID);
		}
		// Read the group, if there is one
		Group group = null;
		if(r.hasNull()) {
			r.readNull();
		} else {
			r.addObjectReader(Types.GROUP, groupReader);
			group = r.readStruct(Types.GROUP, Group.class);
			r.removeObjectReader(Types.GROUP);
		}
		// Read the author, if there is one
		Author author = null;
		if(r.hasNull()) {
			r.readNull();
		} else {
			r.addObjectReader(Types.AUTHOR, authorReader);
			author = r.readStruct(Types.AUTHOR, Author.class);
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
		return new UnverifiedMessageImpl(parent, group, author, subject,
				timestamp, raw, authorSig, groupSig, bodyStart, body.length,
				signedByAuthor, signedByGroup);
	}
}
