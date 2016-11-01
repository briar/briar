package org.briarproject.messaging;

import org.briarproject.api.FormatException;
import org.briarproject.api.clients.ClientHelper;
import org.briarproject.api.data.BdfList;
import org.briarproject.api.messaging.PrivateMessage;
import org.briarproject.api.messaging.PrivateMessageFactory;
import org.briarproject.api.nullsafety.NotNullByDefault;
import org.briarproject.api.sync.GroupId;
import org.briarproject.api.sync.Message;

import javax.inject.Inject;

import static org.briarproject.api.messaging.MessagingConstants.MAX_PRIVATE_MESSAGE_BODY_LENGTH;
import static org.briarproject.util.StringUtils.utf8IsTooLong;

@NotNullByDefault
class PrivateMessageFactoryImpl implements PrivateMessageFactory {

	private final ClientHelper clientHelper;

	@Inject
	PrivateMessageFactoryImpl(ClientHelper clientHelper) {
		this.clientHelper = clientHelper;
	}

	@Override
	public PrivateMessage createPrivateMessage(GroupId groupId, long timestamp,
			String body) throws FormatException {
		// Validate the arguments
		if (utf8IsTooLong(body, MAX_PRIVATE_MESSAGE_BODY_LENGTH))
			throw new IllegalArgumentException();
		// Serialise the message
		BdfList message = BdfList.of(body);
		Message m = clientHelper.createMessage(groupId, timestamp, message);
		return new PrivateMessage(m);
	}
}
