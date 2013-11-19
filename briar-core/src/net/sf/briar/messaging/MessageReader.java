package net.sf.briar.messaging;

import static net.sf.briar.api.AuthorConstants.MAX_SIGNATURE_LENGTH;
import static net.sf.briar.api.messaging.MessagingConstants.MAX_BODY_LENGTH;
import static net.sf.briar.api.messaging.MessagingConstants.MAX_CONTENT_TYPE_LENGTH;
import static net.sf.briar.api.messaging.MessagingConstants.MAX_PACKET_LENGTH;
import static net.sf.briar.api.messaging.MessagingConstants.MAX_SUBJECT_LENGTH;
import static net.sf.briar.api.messaging.MessagingConstants.MESSAGE_SALT_LENGTH;
import static net.sf.briar.api.messaging.Types.MESSAGE;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;

import net.sf.briar.api.Author;
import net.sf.briar.api.FormatException;
import net.sf.briar.api.UniqueId;
import net.sf.briar.api.messaging.Group;
import net.sf.briar.api.messaging.MessageId;
import net.sf.briar.api.messaging.UnverifiedMessage;
import net.sf.briar.api.serial.CopyingConsumer;
import net.sf.briar.api.serial.CountingConsumer;
import net.sf.briar.api.serial.Reader;
import net.sf.briar.api.serial.StructReader;

class MessageReader implements StructReader<UnverifiedMessage> {

	private final StructReader<Group> groupReader;
	private final StructReader<Author> authorReader;
	private final CharsetDecoder decoder;

	MessageReader(StructReader<Group> groupReader,
			StructReader<Author> authorReader) {
		this.groupReader = groupReader;
		this.authorReader = authorReader;
		decoder = Charset.forName("UTF-8").newDecoder();
	}

	public UnverifiedMessage readStruct(Reader r) throws IOException {
		CopyingConsumer copying = new CopyingConsumer();
		CountingConsumer counting = new CountingConsumer(MAX_PACKET_LENGTH);
		r.addConsumer(copying);
		r.addConsumer(counting);
		// Read the start of the struct
		r.readStructStart(MESSAGE);
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
		// Read the content type
		String contentType = r.readString(MAX_CONTENT_TYPE_LENGTH);
		// Read the timestamp
		long timestamp = r.readIntAny();
		if(timestamp < 0) throw new FormatException();
		// Read the salt
		byte[] salt = r.readBytes(MESSAGE_SALT_LENGTH);
		if(salt.length < MESSAGE_SALT_LENGTH) throw new FormatException();
		// Read the message body
		byte[] body = r.readBytes(MAX_BODY_LENGTH);
		// If the content type is text/plain, extract a subject line
		String subject;
		if(contentType.equals("text/plain")) {
			byte[] start = new byte[Math.min(MAX_SUBJECT_LENGTH, body.length)];
			System.arraycopy(body, 0, start, 0, start.length);
			decoder.reset();
			subject = decoder.decode(ByteBuffer.wrap(start)).toString();
		} else {
			subject = "";
		}
		// Record the offset of the body within the message
		int bodyStart = (int) counting.getCount() - body.length;
		// Record the length of the data covered by the author's signature
		int signedLength = (int) counting.getCount();
		// Read the author's signature, if there is one
		byte[] signature = null;
		if(author == null) r.readNull();
		else signature = r.readBytes(MAX_SIGNATURE_LENGTH);
		// Read the end of the struct
		r.readStructEnd();
		// The signature will be verified later
		r.removeConsumer(counting);
		r.removeConsumer(copying);
		byte[] raw = copying.getCopy();
		return new UnverifiedMessage(parent, group, author, contentType,
				subject, timestamp, raw, signature, bodyStart, body.length,
				signedLength);
	}
}
