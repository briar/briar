package org.briarproject.messaging;

import static org.briarproject.api.AuthorConstants.MAX_SIGNATURE_LENGTH;
import static org.briarproject.api.messaging.MessagingConstants.MAX_BODY_LENGTH;
import static org.briarproject.api.messaging.MessagingConstants.MAX_CONTENT_TYPE_LENGTH;
import static org.briarproject.api.messaging.MessagingConstants.MAX_PAYLOAD_LENGTH;
import static org.briarproject.api.messaging.MessagingConstants.MESSAGE_SALT_LENGTH;

import java.io.IOException;

import org.briarproject.api.Author;
import org.briarproject.api.FormatException;
import org.briarproject.api.UniqueId;
import org.briarproject.api.data.ObjectReader;
import org.briarproject.api.data.Reader;
import org.briarproject.api.messaging.Group;
import org.briarproject.api.messaging.MessageId;
import org.briarproject.api.messaging.UnverifiedMessage;

class MessageReader implements ObjectReader<UnverifiedMessage> {

	private final ObjectReader<Group> groupReader;
	private final ObjectReader<Author> authorReader;

	MessageReader(ObjectReader<Group> groupReader,
			ObjectReader<Author> authorReader) {
		this.groupReader = groupReader;
		this.authorReader = authorReader;
	}

	public UnverifiedMessage readObject(Reader r) throws IOException {
		CopyingConsumer copying = new CopyingConsumer();
		CountingConsumer counting = new CountingConsumer(MAX_PAYLOAD_LENGTH);
		r.addConsumer(copying);
		r.addConsumer(counting);
		// Read the start of the message
		r.readListStart();
		// Read the parent's message ID, if there is one
		MessageId parent = null;
		if (r.hasNull()) {
			r.readNull();
		} else {
			byte[] b = r.readRaw(UniqueId.LENGTH);
			if (b.length < UniqueId.LENGTH) throw new FormatException();
			parent = new MessageId(b);
		}
		// Read the group
		Group group = groupReader.readObject(r);
		// Read the author, if there is one
		Author author = null;
		if (r.hasNull()) r.readNull();
		else author = authorReader.readObject(r);
		// Read the content type
		String contentType = r.readString(MAX_CONTENT_TYPE_LENGTH);
		// Read the timestamp
		long timestamp = r.readInteger();
		if (timestamp < 0) throw new FormatException();
		// Read the salt
		byte[] salt = r.readRaw(MESSAGE_SALT_LENGTH);
		if (salt.length < MESSAGE_SALT_LENGTH) throw new FormatException();
		// Read the message body
		byte[] body = r.readRaw(MAX_BODY_LENGTH);
		// Record the offset of the body within the message
		int bodyStart = (int) counting.getCount() - body.length;
		// Record the length of the data covered by the author's signature
		int signedLength = (int) counting.getCount();
		// Read the author's signature, if there is one
		byte[] signature = null;
		if (author == null) r.readNull();
		else signature = r.readRaw(MAX_SIGNATURE_LENGTH);
		// Read the end of the message
		r.readListEnd();
		// Reset the reader
		r.removeConsumer(counting);
		r.removeConsumer(copying);
		// Build and return the unverified message
		byte[] raw = copying.getCopy();
		return new UnverifiedMessage(parent, group, author, contentType,
				timestamp, raw, signature, bodyStart, body.length,
				signedLength);
	}
}
