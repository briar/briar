package org.briarproject.messaging;

import org.briarproject.api.FormatException;
import org.briarproject.api.clients.BdfMessageContext;
import org.briarproject.api.clients.ClientHelper;
import org.briarproject.api.data.BdfDictionary;
import org.briarproject.api.data.BdfList;
import org.briarproject.api.data.MetadataEncoder;
import org.briarproject.api.sync.Group;
import org.briarproject.api.sync.Message;
import org.briarproject.api.system.Clock;
import org.briarproject.clients.BdfMessageValidator;

import static org.briarproject.api.messaging.MessagingConstants.MAX_PRIVATE_MESSAGE_BODY_LENGTH;
import static org.briarproject.clients.BdfConstants.MSG_KEY_READ;

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
