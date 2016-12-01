package org.briarproject.briar.client;

import org.briarproject.bramble.api.FormatException;
import org.briarproject.bramble.api.client.BdfMessageContext;
import org.briarproject.bramble.api.client.ClientHelper;
import org.briarproject.bramble.api.data.BdfList;
import org.briarproject.bramble.api.data.MetadataEncoder;
import org.briarproject.bramble.api.db.Metadata;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.sync.Group;
import org.briarproject.bramble.api.sync.InvalidMessageException;
import org.briarproject.bramble.api.sync.Message;
import org.briarproject.bramble.api.sync.MessageContext;
import org.briarproject.bramble.api.system.Clock;
import org.briarproject.briar.api.client.MessageQueueManager.QueueMessageValidator;
import org.briarproject.briar.api.client.QueueMessage;

import java.util.logging.Logger;

import javax.annotation.concurrent.Immutable;

import static org.briarproject.bramble.api.transport.TransportConstants.MAX_CLOCK_DIFFERENCE;
import static org.briarproject.briar.api.client.QueueMessage.QUEUE_MESSAGE_HEADER_LENGTH;

@Deprecated
@Immutable
@NotNullByDefault
public abstract class BdfQueueMessageValidator
		implements QueueMessageValidator {

	protected static final Logger LOG =
			Logger.getLogger(BdfQueueMessageValidator.class.getName());

	protected final ClientHelper clientHelper;
	protected final MetadataEncoder metadataEncoder;
	protected final Clock clock;

	protected BdfQueueMessageValidator(ClientHelper clientHelper,
			MetadataEncoder metadataEncoder, Clock clock) {
		this.clientHelper = clientHelper;
		this.metadataEncoder = metadataEncoder;
		this.clock = clock;
	}

	protected abstract BdfMessageContext validateMessage(Message m, Group g,
			BdfList body) throws InvalidMessageException, FormatException;

	@Override
	public MessageContext validateMessage(QueueMessage q, Group g)
			throws InvalidMessageException {
		// Reject the message if it's too far in the future
		long now = clock.currentTimeMillis();
		if (q.getTimestamp() - now > MAX_CLOCK_DIFFERENCE) {
			throw new InvalidMessageException(
					"Timestamp is too far in the future");
		}
		byte[] raw = q.getRaw();
		if (raw.length <= QUEUE_MESSAGE_HEADER_LENGTH) {
			throw new InvalidMessageException("Message is too short");
		}
		try {
			BdfList body = clientHelper.toList(raw, QUEUE_MESSAGE_HEADER_LENGTH,
					raw.length - QUEUE_MESSAGE_HEADER_LENGTH);
			BdfMessageContext result = validateMessage(q, g, body);
			Metadata meta = metadataEncoder.encode(result.getDictionary());
			return new MessageContext(meta, result.getDependencies());
		} catch (FormatException e) {
			throw new InvalidMessageException(e);
		}
	}
}
