package org.briarproject.messaging;

import org.briarproject.api.FormatException;
import org.briarproject.api.UniqueId;
import org.briarproject.api.data.BdfDictionary;
import org.briarproject.api.data.BdfReader;
import org.briarproject.api.data.BdfReaderFactory;
import org.briarproject.api.data.MetadataEncoder;
import org.briarproject.api.db.Metadata;
import org.briarproject.api.sync.Message;
import org.briarproject.api.sync.MessageId;
import org.briarproject.api.sync.MessageValidator;
import org.briarproject.api.system.Clock;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.logging.Logger;

import javax.inject.Inject;

import static org.briarproject.api.messaging.MessagingConstants.MAX_CONTENT_TYPE_LENGTH;
import static org.briarproject.api.messaging.MessagingConstants.MAX_PRIVATE_MESSAGE_BODY_LENGTH;
import static org.briarproject.api.sync.SyncConstants.MESSAGE_HEADER_LENGTH;
import static org.briarproject.api.transport.TransportConstants.MAX_CLOCK_DIFFERENCE;

class PrivateMessageValidator implements MessageValidator {

	private static final Logger LOG =
			Logger.getLogger(PrivateMessageValidator.class.getName());

	private final BdfReaderFactory bdfReaderFactory;
	private final MetadataEncoder metadataEncoder;
	private final Clock clock;

	@Inject
	PrivateMessageValidator(BdfReaderFactory bdfReaderFactory,
			MetadataEncoder metadataEncoder, Clock clock) {
		this.bdfReaderFactory = bdfReaderFactory;
		this.metadataEncoder = metadataEncoder;
		this.clock = clock;
	}

	@Override
	public Metadata validateMessage(Message m) {
		// Reject the message if it's too far in the future
		long now = clock.currentTimeMillis();
		if (m.getTimestamp() - now > MAX_CLOCK_DIFFERENCE) {
			LOG.info("Timestamp is too far in the future");
			return null;
		}
		try {
			// Parse the message body
			byte[] raw = m.getRaw();
			ByteArrayInputStream in = new ByteArrayInputStream(raw,
					MESSAGE_HEADER_LENGTH, raw.length - MESSAGE_HEADER_LENGTH);
			BdfReader r = bdfReaderFactory.createReader(in);
			MessageId parent = null;
			String contentType;
			r.readListStart();
			// Read the parent ID, if any
			if (r.hasRaw()) {
				byte[] id = r.readRaw(UniqueId.LENGTH);
				if (id.length < UniqueId.LENGTH) throw new FormatException();
				parent = new MessageId(id);
			} else {
				r.readNull();
			}
			// Read the content type
			contentType = r.readString(MAX_CONTENT_TYPE_LENGTH);
			// Read the private message body
			r.readRaw(MAX_PRIVATE_MESSAGE_BODY_LENGTH);
			r.readListEnd();
			if (!r.eof()) throw new FormatException();
			// Return the metadata
			BdfDictionary d = new BdfDictionary();
			d.put("timestamp", m.getTimestamp());
			if (parent != null) d.put("parent", parent.getBytes());
			d.put("contentType", contentType);
			d.put("local", false);
			d.put("read", false);
			return metadataEncoder.encode(d);
		} catch (IOException e) {
			LOG.info("Invalid private message");
			return null;
		}
	}
}
