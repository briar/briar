package org.briarproject.briar.messaging;

import org.briarproject.bramble.api.FormatException;
import org.briarproject.bramble.api.client.ClientHelper;
import org.briarproject.bramble.api.data.BdfList;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.sync.GroupId;
import org.briarproject.bramble.api.sync.Message;
import org.briarproject.briar.api.messaging.PrivateMessage;
import org.briarproject.briar.api.messaging.PrivateMessageFactory;

import javax.annotation.concurrent.Immutable;
import javax.inject.Inject;

import static org.briarproject.bramble.util.StringUtils.utf8IsTooLong;
import static org.briarproject.briar.api.messaging.MessagingConstants.MAX_PRIVATE_MESSAGE_TEXT_LENGTH;

@Immutable
@NotNullByDefault
class PrivateMessageFactoryImpl implements PrivateMessageFactory {

	private final ClientHelper clientHelper;

	@Inject
	PrivateMessageFactoryImpl(ClientHelper clientHelper) {
		this.clientHelper = clientHelper;
	}

	@Override
	public PrivateMessage createPrivateMessage(GroupId groupId, long timestamp,
			String text) throws FormatException {
		// Validate the arguments
		if (utf8IsTooLong(text, MAX_PRIVATE_MESSAGE_TEXT_LENGTH))
			throw new IllegalArgumentException();
		// Serialise the message
		BdfList message = BdfList.of(text);
		Message m = clientHelper.createMessage(groupId, timestamp, message);
		return new PrivateMessage(m);
	}
}
