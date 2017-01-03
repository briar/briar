package org.briarproject.briar.sharing;

import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.sync.GroupId;
import org.briarproject.bramble.api.sync.MessageId;
import org.briarproject.briar.api.sharing.Shareable;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

@Immutable
@NotNullByDefault
class InviteMessage<S extends Shareable> extends SharingMessage {

	private final S shareable;
	@Nullable
	private final String message;

	InviteMessage(MessageId id, @Nullable MessageId previousMessageId,
			GroupId contactGroupId, S shareable, @Nullable String message,
			long timestamp) {
		super(id, contactGroupId, shareable.getId(), timestamp,
				previousMessageId);
		if (message != null && message.equals(""))
			throw new IllegalArgumentException();
		this.shareable = shareable;
		this.message = message;
	}

	public S getShareable() {
		return shareable;
	}

	@Nullable
	public String getMessage() {
		return message;
	}

}
