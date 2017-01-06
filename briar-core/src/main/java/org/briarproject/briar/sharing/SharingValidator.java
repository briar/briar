package org.briarproject.briar.sharing;

import org.briarproject.bramble.api.FormatException;
import org.briarproject.bramble.api.UniqueId;
import org.briarproject.bramble.api.client.BdfMessageContext;
import org.briarproject.bramble.api.client.BdfMessageValidator;
import org.briarproject.bramble.api.client.ClientHelper;
import org.briarproject.bramble.api.data.BdfDictionary;
import org.briarproject.bramble.api.data.BdfList;
import org.briarproject.bramble.api.data.MetadataEncoder;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.sync.Group;
import org.briarproject.bramble.api.sync.GroupId;
import org.briarproject.bramble.api.sync.Message;
import org.briarproject.bramble.api.sync.MessageId;
import org.briarproject.bramble.api.system.Clock;

import java.util.Collections;

import javax.annotation.concurrent.Immutable;

import static org.briarproject.bramble.util.ValidationUtils.checkLength;
import static org.briarproject.bramble.util.ValidationUtils.checkSize;
import static org.briarproject.briar.api.sharing.SharingConstants.MAX_INVITATION_MESSAGE_LENGTH;
import static org.briarproject.briar.sharing.MessageType.INVITE;

@Immutable
@NotNullByDefault
abstract class SharingValidator extends BdfMessageValidator {

	private final MessageEncoder messageEncoder;

	SharingValidator(MessageEncoder messageEncoder, ClientHelper clientHelper,
			MetadataEncoder metadataEncoder, Clock clock) {
		super(clientHelper, metadataEncoder, clock);
		this.messageEncoder = messageEncoder;
	}

	@Override
	protected BdfMessageContext validateMessage(Message m, Group g,
			BdfList body) throws FormatException {
		MessageType type = MessageType.fromValue(body.getLong(0).intValue());
		switch (type) {
			case INVITE:
				return validateInviteMessage(m, body);
			case ACCEPT:
			case DECLINE:
			case LEAVE:
			case ABORT:
				return validateNonInviteMessage(type, m, body);
			default:
				throw new FormatException();
		}
	}

	private BdfMessageContext validateInviteMessage(Message m, BdfList body)
			throws FormatException {
		checkSize(body, 4);
		byte[] previousMessageId = body.getOptionalRaw(1);
		checkLength(previousMessageId, UniqueId.LENGTH);
		BdfList descriptor = body.getList(2);
		GroupId shareableId = validateDescriptor(descriptor);
		String msg = body.getOptionalString(3);
		checkLength(msg, 1, MAX_INVITATION_MESSAGE_LENGTH);

		BdfDictionary meta = messageEncoder
				.encodeMetadata(INVITE, shareableId, m.getTimestamp(), false,
						false, false, false, false);
		if (previousMessageId == null) {
			return new BdfMessageContext(meta);
		} else {
			MessageId dependency = new MessageId(previousMessageId);
			return new BdfMessageContext(meta,
					Collections.singletonList(dependency));
		}
	}

	protected abstract GroupId validateDescriptor(BdfList descriptor)
			throws FormatException;

	private BdfMessageContext validateNonInviteMessage(MessageType type,
			Message m, BdfList body) throws FormatException {
		checkSize(body, 3);
		byte[] shareableId = body.getRaw(1);
		checkLength(shareableId, UniqueId.LENGTH);
		byte[] previousMessageId = body.getOptionalRaw(2);
		checkLength(previousMessageId, UniqueId.LENGTH);

		BdfDictionary meta = messageEncoder
				.encodeMetadata(type, new GroupId(shareableId),
						m.getTimestamp(), false, false, false, false, false);
		if (previousMessageId == null) {
			return new BdfMessageContext(meta);
		} else {
			MessageId dependency = new MessageId(previousMessageId);
			return new BdfMessageContext(meta,
					Collections.singletonList(dependency));
		}
	}

}
