package org.briarproject.briar.messaging;

import org.briarproject.bramble.api.FormatException;
import org.briarproject.bramble.api.client.BdfMessageContext;
import org.briarproject.bramble.api.client.BdfMessageValidator;
import org.briarproject.bramble.api.client.ClientHelper;
import org.briarproject.bramble.api.data.BdfDictionary;
import org.briarproject.bramble.api.data.BdfList;
import org.briarproject.bramble.api.data.MetadataEncoder;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.sync.Group;
import org.briarproject.bramble.api.sync.Message;
import org.briarproject.bramble.api.system.Clock;

import javax.annotation.concurrent.Immutable;

import static org.briarproject.bramble.util.ValidationUtils.checkLength;
import static org.briarproject.bramble.util.ValidationUtils.checkSize;
import static org.briarproject.briar.api.messaging.MessagingConstants.MAX_PRIVATE_MESSAGE_BODY_LENGTH;
import static org.briarproject.briar.client.MessageTrackerConstants.MSG_KEY_READ;

@Immutable
@NotNullByDefault
class PrivateMessageValidator extends BdfMessageValidator {

	PrivateMessageValidator(ClientHelper clientHelper,
			MetadataEncoder metadataEncoder, Clock clock) {
		super(clientHelper, metadataEncoder, clock);
	}

	@Override
	protected BdfMessageContext validateMessage(Message m, Group g,
			BdfList body) throws FormatException {
		// private message body
		checkSize(body, 1);
		// Private message body
		String privateMessageBody = body.getString(0);
		checkLength(privateMessageBody, 0, MAX_PRIVATE_MESSAGE_BODY_LENGTH);
		// Return the metadata
		BdfDictionary meta = new BdfDictionary();
		meta.put("timestamp", m.getTimestamp());
		meta.put("local", false);
		meta.put(MSG_KEY_READ, false);
		return new BdfMessageContext(meta);
	}
}
