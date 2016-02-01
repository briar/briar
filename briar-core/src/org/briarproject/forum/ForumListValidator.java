package org.briarproject.forum;

import org.briarproject.api.FormatException;
import org.briarproject.api.data.BdfDictionary;
import org.briarproject.api.data.BdfReader;
import org.briarproject.api.data.BdfReaderFactory;
import org.briarproject.api.data.MetadataEncoder;
import org.briarproject.api.db.Metadata;
import org.briarproject.api.sync.Group;
import org.briarproject.api.sync.Message;
import org.briarproject.api.sync.MessageValidator;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.logging.Logger;

import static org.briarproject.api.forum.ForumConstants.FORUM_SALT_LENGTH;
import static org.briarproject.api.forum.ForumConstants.MAX_FORUM_NAME_LENGTH;
import static org.briarproject.api.sync.SyncConstants.MESSAGE_HEADER_LENGTH;

class ForumListValidator implements MessageValidator {

	private static final Logger LOG =
			Logger.getLogger(ForumListValidator.class.getName());

	private final BdfReaderFactory bdfReaderFactory;
	private final MetadataEncoder metadataEncoder;

	ForumListValidator(BdfReaderFactory bdfReaderFactory,
			MetadataEncoder metadataEncoder) {
		this.bdfReaderFactory = bdfReaderFactory;
		this.metadataEncoder = metadataEncoder;
	}

	@Override
	public Metadata validateMessage(Message m, Group g) {
		try {
			// Parse the message body
			byte[] raw = m.getRaw();
			ByteArrayInputStream in = new ByteArrayInputStream(raw,
					MESSAGE_HEADER_LENGTH, raw.length - MESSAGE_HEADER_LENGTH);
			BdfReader r = bdfReaderFactory.createReader(in);
			r.readListStart();
			long version = r.readInteger();
			if (version < 0) throw new FormatException();
			r.readListStart();
			while (!r.hasListEnd()) {
				r.readListStart();
				String name = r.readString(MAX_FORUM_NAME_LENGTH);
				if (name.length() == 0) throw new FormatException();
				byte[] salt = r.readRaw(FORUM_SALT_LENGTH);
				if (salt.length != FORUM_SALT_LENGTH)
					throw new FormatException();
				r.readListEnd();
			}
			r.readListEnd();
			r.readListEnd();
			if (!r.eof()) throw new FormatException();
			// Return the metadata
			BdfDictionary d = new BdfDictionary();
			d.put("version", version);
			d.put("local", false);
			return metadataEncoder.encode(d);
		} catch (IOException e) {
			LOG.info("Invalid forum list");
			return null;
		}
	}
}
