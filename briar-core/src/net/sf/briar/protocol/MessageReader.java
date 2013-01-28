package net.sf.briar.protocol;

import static net.sf.briar.api.protocol.ProtocolConstants.MAX_BODY_LENGTH;
import static net.sf.briar.api.protocol.ProtocolConstants.MAX_PACKET_LENGTH;
import static net.sf.briar.api.protocol.ProtocolConstants.MAX_SIGNATURE_LENGTH;
import static net.sf.briar.api.protocol.ProtocolConstants.MAX_SUBJECT_LENGTH;
import static net.sf.briar.api.protocol.ProtocolConstants.SALT_LENGTH;
import static net.sf.briar.api.protocol.Types.MESSAGE;

import java.io.IOException;

import net.sf.briar.api.FormatException;
import net.sf.briar.api.protocol.Author;
import net.sf.briar.api.protocol.Group;
import net.sf.briar.api.protocol.MessageId;
import net.sf.briar.api.protocol.UniqueId;
import net.sf.briar.api.protocol.UnverifiedMessage;
import net.sf.briar.api.serial.CopyingConsumer;
import net.sf.briar.api.serial.CountingConsumer;
import net.sf.briar.api.serial.Reader;
import net.sf.briar.api.serial.StructReader;

class MessageReader implements StructReader<UnverifiedMessage> {

	private final StructReader<Group> groupReader;
	private final StructReader<Author> authorReader;

	MessageReader(StructReader<Group> groupReader,
			StructReader<Author> authorReader) {
		this.groupReader = groupReader;
		this.authorReader = authorReader;
	}

	public UnverifiedMessage readStruct(Reader r) throws IOException {
		CopyingConsumer copying = new CopyingConsumer();
		CountingConsumer counting = new CountingConsumer(MAX_PACKET_LENGTH);
		r.addConsumer(copying);
		r.addConsumer(counting);
		// Read the initial tag
		r.readStructId(MESSAGE);
		// Read the parent's message ID, if there is one
		MessageId parent = null;
		if(r.hasNull()) {
			r.readNull();
		} else {
			byte[] b = r.readBytes(UniqueId.LENGTH);
			if(b.length < UniqueId.LENGTH) throw new FormatException();
			parent = new MessageId(b);
		}
		// Read the group, if there is one
		Group group = null;
		if(r.hasNull()) r.readNull();
		else group = groupReader.readStruct(r);
		// Read the author, if there is one
		Author author = null;
		if(r.hasNull()) r.readNull();
		else author = authorReader.readStruct(r);
		// Read the subject
		String subject = r.readString(MAX_SUBJECT_LENGTH);
		// Read the timestamp
		long timestamp = r.readInt64();
		if(timestamp < 0L) throw new FormatException();
		// Read the salt
		byte[] salt = r.readBytes(SALT_LENGTH);
		if(salt.length < SALT_LENGTH) throw new FormatException();
		// Read the message body
		byte[] body = r.readBytes(MAX_BODY_LENGTH);
		// Record the offset of the body within the message
		int bodyStart = (int) counting.getCount() - body.length;
		// Record the length of the data covered by the author's signature
		int signedByAuthor = (int) counting.getCount();
		// Read the author's signature, if there is one
		byte[] authorSig = null;
		if(author == null) r.readNull();
		else authorSig = r.readBytes(MAX_SIGNATURE_LENGTH);
		// Record the length of the data covered by the group's signature
		int signedByGroup = (int) counting.getCount();
		// Read the group's signature, if there is one
		byte[] groupSig = null;
		if(group == null || group.getPublicKey() == null) r.readNull();
		else groupSig = r.readBytes(MAX_SIGNATURE_LENGTH);
		// That's all, folks
		r.removeConsumer(counting);
		r.removeConsumer(copying);
		byte[] raw = copying.getCopy();
		return new UnverifiedMessageImpl(parent, group, author, subject,
				timestamp, raw, authorSig, groupSig, bodyStart, body.length,
				signedByAuthor, signedByGroup);
	}
}
