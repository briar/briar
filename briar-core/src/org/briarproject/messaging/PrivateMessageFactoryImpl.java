package org.briarproject.messaging;

import org.briarproject.api.FormatException;
import org.briarproject.api.clients.ClientHelper;
import org.briarproject.api.data.BdfList;
import org.briarproject.api.messaging.PrivateMessage;
import org.briarproject.api.messaging.PrivateMessageFactory;
import org.briarproject.api.sync.GroupId;
import org.briarproject.api.sync.Message;
import org.briarproject.api.sync.MessageId;
import org.briarproject.util.StringUtils;

import javax.inject.Inject;

import static org.briarproject.api.messaging.MessagingConstants.MAX_CONTENT_TYPE_LENGTH;
import static org.briarproject.api.messaging.MessagingConstants.MAX_PRIVATE_MESSAGE_BODY_LENGTH;

class PrivateMessageFactoryImpl implements PrivateMessageFactory {

	private final ClientHelper clientHelper;

	@Inject
	PrivateMessageFactoryImpl(ClientHelper clientHelper) {
		this.clientHelper = clientHelper;
	}

	@Override
	public PrivateMessage createPrivateMessage(GroupId groupId, long timestamp,
			MessageId parent, String contentType, byte[] body)
			throws FormatException {
		// Validate the arguments
		if (StringUtils.toUtf8(contentType).length > MAX_CONTENT_TYPE_LENGTH)
			throw new IllegalArgumentException();
		if (body.length > MAX_PRIVATE_MESSAGE_BODY_LENGTH)
			throw new IllegalArgumentException();
		// Serialise the message
		BdfList message = BdfList.of(parent, contentType, body);
		Message m = clientHelper.createMessage(groupId, timestamp, message);
		return new PrivateMessage(m, parent, contentType);
	}
}
