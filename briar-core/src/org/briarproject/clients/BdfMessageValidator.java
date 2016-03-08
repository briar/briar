package org.briarproject.clients;

import org.briarproject.api.FormatException;
import org.briarproject.api.clients.ClientHelper;
import org.briarproject.api.clients.QueueMessage;
import org.briarproject.api.clients.QueueMessageValidator;
import org.briarproject.api.data.BdfDictionary;
import org.briarproject.api.data.BdfList;
import org.briarproject.api.data.MetadataEncoder;
import org.briarproject.api.db.Metadata;
import org.briarproject.api.sync.Group;
import org.briarproject.api.sync.Message;
import org.briarproject.api.sync.MessageValidator;
import org.briarproject.api.system.Clock;
import org.briarproject.util.StringUtils;

import java.util.logging.Logger;

import static org.briarproject.api.clients.QueueMessage.QUEUE_MESSAGE_HEADER_LENGTH;
import static org.briarproject.api.sync.SyncConstants.MESSAGE_HEADER_LENGTH;
import static org.briarproject.api.transport.TransportConstants.MAX_CLOCK_DIFFERENCE;

public abstract class BdfMessageValidator implements MessageValidator,
		QueueMessageValidator {

	protected static final Logger LOG =
			Logger.getLogger(BdfMessageValidator.class.getName());

	protected final ClientHelper clientHelper;
	protected final MetadataEncoder metadataEncoder;
	protected final Clock clock;

	protected BdfMessageValidator(ClientHelper clientHelper,
			MetadataEncoder metadataEncoder, Clock clock) {
		this.clientHelper = clientHelper;
		this.metadataEncoder = metadataEncoder;
		this.clock = clock;
	}

	protected abstract BdfDictionary validateMessage(BdfList message, Group g,
			long timestamp) throws FormatException;

	@Override
	public Metadata validateMessage(Message m, Group g) {
		return validateMessage(m, g, MESSAGE_HEADER_LENGTH);
	}

	@Override
	public Metadata validateMessage(QueueMessage q, Group g) {
		return validateMessage(q, g, QUEUE_MESSAGE_HEADER_LENGTH);
	}

	private Metadata validateMessage(Message m, Group g, int headerLength) {
		// Reject the message if it's too far in the future
		long now = clock.currentTimeMillis();
		if (m.getTimestamp() - now > MAX_CLOCK_DIFFERENCE) {
			LOG.info("Timestamp is too far in the future");
			return null;
		}
		byte[] raw = m.getRaw();
		if (raw.length <= headerLength) {
			LOG.info("Message is too short");
			return null;
		}
		try {
			BdfList message = clientHelper.toList(raw, headerLength,
					raw.length - headerLength);
			BdfDictionary meta = validateMessage(message, g, m.getTimestamp());
			if (meta == null) {
				LOG.info("Invalid message");
				return null;
			}
			return metadataEncoder.encode(meta);
		} catch (FormatException e) {
			LOG.info("Invalid message");
			return null;
		}
	}

	protected void checkLength(String s, int minLength, int maxLength)
			throws FormatException {
		if (s != null) {
			int length = StringUtils.toUtf8(s).length;
			if (length < minLength) throw new FormatException();
			if (length > maxLength) throw new FormatException();
		}
	}

	protected void checkLength(String s, int length) throws FormatException {
		if (s != null && StringUtils.toUtf8(s).length != length)
			throw new FormatException();
	}

	protected void checkLength(byte[] b, int minLength, int maxLength)
			throws FormatException {
		if (b != null) {
			if (b.length < minLength) throw new FormatException();
			if (b.length > maxLength) throw new FormatException();
		}
	}

	protected void checkLength(byte[] b, int length) throws FormatException {
		if (b != null && b.length != length) throw new FormatException();
	}

	protected void checkSize(BdfList list, int minSize, int maxSize)
		throws FormatException {
		if (list != null) {
			if (list.size() < minSize) throw new FormatException();
			if (list.size() > maxSize) throw new FormatException();
		}
	}

	protected void checkSize(BdfList list, int size) throws FormatException {
		if (list != null && list.size() != size) throw new FormatException();
	}

	protected void checkSize(BdfDictionary dictionary, int minSize,
			int maxSize) throws FormatException {
		if (dictionary != null) {
			if (dictionary.size() < minSize) throw new FormatException();
			if (dictionary.size() > maxSize) throw new FormatException();
		}
	}

	protected void checkSize(BdfDictionary dictionary, int size)
			throws FormatException {
		if (dictionary != null && dictionary.size() != size)
			throw new FormatException();
	}
}
