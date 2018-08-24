package org.briarproject.briar.headless.messaging;

import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.briar.api.messaging.PrivateMessage;
import org.briarproject.briar.api.messaging.PrivateMessageHeader;

import javax.annotation.concurrent.Immutable;

@Immutable
@NotNullByDefault
@SuppressWarnings("WeakerAccess")
public class OutputPrivateMessage {

	public final String body;
	public final long timestamp;
	public final boolean read, seen, sent, local;
	public final byte[] id, groupId;

	OutputPrivateMessage(PrivateMessageHeader header, String body) {
		this.body = body;
		this.timestamp = header.getTimestamp();
		this.read = header.isRead();
		this.seen = header.isSeen();
		this.sent = header.isSent();
		this.local = header.isLocal();
		this.id = header.getId().getBytes();
		this.groupId = header.getGroupId().getBytes();
	}

	/**
	 * Only meant for own {@link PrivateMessage}s directly after creation.
	 */
	OutputPrivateMessage(PrivateMessage m, String body) {
		this.body = body;
		this.timestamp = m.getMessage().getTimestamp();
		this.read = true;
		this.seen = true;
		this.sent = false;
		this.local = true;
		this.id = m.getMessage().getId().getBytes();
		this.groupId = m.getMessage().getGroupId().getBytes();
	}
}
