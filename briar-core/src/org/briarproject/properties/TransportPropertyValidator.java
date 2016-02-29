package org.briarproject.properties;

import org.briarproject.api.FormatException;
import org.briarproject.api.UniqueId;
import org.briarproject.api.data.BdfDictionary;
import org.briarproject.api.data.BdfReader;
import org.briarproject.api.data.BdfReaderFactory;
import org.briarproject.api.data.MetadataEncoder;
import org.briarproject.api.db.Metadata;
import org.briarproject.api.sync.Group;
import org.briarproject.api.sync.Message;
import org.briarproject.api.sync.MessageValidator;
import org.briarproject.api.system.Clock;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.logging.Logger;

import static org.briarproject.api.TransportId.MAX_TRANSPORT_ID_LENGTH;
import static org.briarproject.api.properties.TransportPropertyConstants.MAX_PROPERTIES_PER_TRANSPORT;
import static org.briarproject.api.properties.TransportPropertyConstants.MAX_PROPERTY_LENGTH;
import static org.briarproject.api.sync.SyncConstants.MESSAGE_HEADER_LENGTH;
import static org.briarproject.api.transport.TransportConstants.MAX_CLOCK_DIFFERENCE;

class TransportPropertyValidator implements MessageValidator {

	private static final Logger LOG =
			Logger.getLogger(TransportPropertyValidator.class.getName());

	private final BdfReaderFactory bdfReaderFactory;
	private final MetadataEncoder metadataEncoder;
	private final Clock clock;

	TransportPropertyValidator(BdfReaderFactory bdfReaderFactory,
			MetadataEncoder metadataEncoder, Clock clock) {
		this.bdfReaderFactory = bdfReaderFactory;
		this.metadataEncoder = metadataEncoder;
		this.clock = clock;
	}

	@Override
	public Metadata validateMessage(Message m, Group g) {
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
			r.readListStart();
			byte[] deviceId = r.readRaw(UniqueId.LENGTH);
			if (deviceId.length != UniqueId.LENGTH) throw new FormatException();
			String transportId = r.readString(MAX_TRANSPORT_ID_LENGTH);
			if (transportId.length() == 0) throw new FormatException();
			long version = r.readLong();
			if (version < 0) throw new FormatException();
			r.readDictionaryStart();
			for (int i = 0; !r.hasDictionaryEnd(); i++) {
				if (i == MAX_PROPERTIES_PER_TRANSPORT)
					throw new FormatException();
				r.readString(MAX_PROPERTY_LENGTH);
				r.readString(MAX_PROPERTY_LENGTH);
			}
			r.readDictionaryEnd();
			r.readListEnd();
			if (!r.eof()) throw new FormatException();
			// Return the metadata
			BdfDictionary d = new BdfDictionary();
			d.put("transportId", transportId);
			d.put("version", version);
			d.put("local", false);
			return metadataEncoder.encode(d);
		} catch (IOException e) {
			LOG.info("Invalid transport update");
			return null;
		}
	}
}
