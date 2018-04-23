package org.briarproject.briar.introduction;

import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.sync.GroupId;
import org.briarproject.bramble.api.sync.MessageId;
import org.briarproject.briar.api.client.SessionId;

import javax.annotation.Nullable;

@NotNullByDefault
interface PeerSession {

	SessionId getSessionId();

	GroupId getContactGroupId();

	long getLocalTimestamp();

	@Nullable
	MessageId getLastLocalMessageId();

	@Nullable
	MessageId getLastRemoteMessageId();

}
