package org.briarproject.clients;

import org.briarproject.api.FormatException;
import org.briarproject.api.clients.BdfMessageContext;
import org.briarproject.api.clients.ClientHelper;
import org.briarproject.api.clients.MessageQueueManager.QueueMessageValidator;
import org.briarproject.api.clients.QueueMessage;
import org.briarproject.api.data.BdfDictionary;
import org.briarproject.api.data.BdfList;
import org.briarproject.api.data.MetadataEncoder;
import org.briarproject.api.db.Metadata;
import org.briarproject.api.nullsafety.NotNullByDefault;
import org.briarproject.api.sync.Group;
import org.briarproject.api.sync.InvalidMessageException;
import org.briarproject.api.sync.Message;
import org.briarproject.api.sync.MessageContext;
import org.briarproject.api.sync.ValidationManager.MessageValidator;
import org.briarproject.api.system.Clock;
import org.briarproject.util.StringUtils;

import java.util.logging.Logger;

import javax.annotation.Nullable;

import static org.briarproject.api.clients.QueueMessage.QUEUE_MESSAGE_HEADER_LENGTH;
import static org.briarproject.api.sync.SyncConstants.MESSAGE_HEADER_LENGTH;
import static org.briarproject.api.transport.TransportConstants.MAX_CLOCK_DIFFERENCE;

@NotNullByDefault
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

	protected abstract BdfMessageContext validateMessage(Message m, Group g,
			BdfList body) throws InvalidMessageException, FormatException;

	@Override
	public MessageContext validateMessage(Message m, Group g)
			throws InvalidMessageException {
		return validateMessage(m, g, MESSAGE_HEADER_LENGTH);
	}

	@Override
	public MessageContext validateMessage(QueueMessage q, Group g)
			throws InvalidMessageException {
		return validateMessage(q, g, QUEUE_MESSAGE_HEADER_LENGTH);
	}

	private MessageContext validateMessage(Message m, Group g, int headerLength)
			throws InvalidMessageException {
		// Reject the message if it's too far in the future
		long now = clock.currentTimeMillis();
		if (m.getTimestamp() - now > MAX_CLOCK_DIFFERENCE) {
			throw new InvalidMessageException(
					"Timestamp is too far in the future");
		}
		byte[] raw = m.getRaw();
		if (raw.length <= headerLength) {
			throw new InvalidMessageException("Message is too short");
		}
		try {
			BdfList body = clientHelper.toList(raw, headerLength,
					raw.length - headerLength);
			BdfMessageContext result = validateMessage(m, g, body);
			Metadata meta = metadataEncoder.encode(result.getDictionary());
			return new MessageContext(meta, result.getDependencies());
		} catch (FormatException e) {
			throw new InvalidMessageException(e);
		}
	}

	protected void checkLength(@Nullable String s, int minLength, int maxLength)
			throws FormatException {
		if (s != null) {
			int length = StringUtils.toUtf8(s).length;
			if (length < minLength) throw new FormatException();
			if (length > maxLength) throw new FormatException();
		}
	}

	protected void checkLength(@Nullable String s, int length)
			throws FormatException {
		if (s != null && StringUtils.toUtf8(s).length != length)
			throw new FormatException();
	}

	protected void checkLength(@Nullable byte[] b, int minLength, int maxLength)
			throws FormatException {
		if (b != null) {
			if (b.length < minLength) throw new FormatException();
			if (b.length > maxLength) throw new FormatException();
		}
	}

	protected void checkLength(@Nullable byte[] b, int length)
			throws FormatException {
		if (b != null && b.length != length) throw new FormatException();
	}

	protected void checkSize(@Nullable BdfList list, int minSize, int maxSize)
			throws FormatException {
		if (list != null) {
			if (list.size() < minSize) throw new FormatException();
			if (list.size() > maxSize) throw new FormatException();
		}
	}

	protected void checkSize(@Nullable BdfList list, int size)
			throws FormatException {
		if (list != null && list.size() != size) throw new FormatException();
	}

	protected void checkSize(@Nullable BdfDictionary dictionary, int minSize,
			int maxSize) throws FormatException {
		if (dictionary != null) {
			if (dictionary.size() < minSize) throw new FormatException();
			if (dictionary.size() > maxSize) throw new FormatException();
		}
	}

	protected void checkSize(@Nullable BdfDictionary dictionary, int size)
			throws FormatException {
		if (dictionary != null && dictionary.size() != size)
			throw new FormatException();
	}
}
