package org.briarproject.messaging;

import org.briarproject.api.FormatException;
import org.briarproject.api.UniqueId;
import org.briarproject.api.clients.ClientHelper;
import org.briarproject.api.data.BdfDictionary;
import org.briarproject.api.data.BdfList;
import org.briarproject.api.data.MetadataEncoder;
import org.briarproject.api.sync.Group;
import org.briarproject.api.sync.Message;
import org.briarproject.api.system.Clock;
import org.briarproject.clients.BdfMessageValidator;

import static org.briarproject.api.messaging.MessagingConstants.MAX_CONTENT_TYPE_LENGTH;
import static org.briarproject.api.messaging.MessagingConstants.MAX_PRIVATE_MESSAGE_BODY_LENGTH;

class PrivateMessageValidator extends BdfMessageValidator {

	PrivateMessageValidator(ClientHelper clientHelper,
			MetadataEncoder metadataEncoder, Clock clock) {
		super(clientHelper, metadataEncoder, clock);
	}

	@Override
	protected BdfDictionary validateMessage(Message m, Group g,
			BdfList body) throws FormatException {
		// Parent ID, content type, private message body
		checkSize(body, 3);
		// Parent ID is optional
		byte[] parentId = body.getOptionalRaw(0);
		checkLength(parentId, UniqueId.LENGTH);
		// Content type
		String contentType = body.getString(1);
		checkLength(contentType, 0, MAX_CONTENT_TYPE_LENGTH);
		// Private message body
		byte[] privateMessageBody = body.getRaw(2);
		checkLength(privateMessageBody, 0, MAX_PRIVATE_MESSAGE_BODY_LENGTH);
		// Return the metadata
		BdfDictionary meta = new BdfDictionary();
		meta.put("timestamp", m.getTimestamp());
		if (parentId != null) meta.put("parent", parentId);
		meta.put("contentType", contentType);
		meta.put("local", false);
		meta.put("read", false);
		return meta;
	}
}
