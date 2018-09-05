package org.briarproject.briar.api.introduction;

import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.sync.GroupId;
import org.briarproject.bramble.api.sync.MessageId;
import org.briarproject.briar.api.client.SessionId;
import org.briarproject.briar.api.messaging.PrivateResponse;

import javax.annotation.concurrent.Immutable;

@Immutable
@NotNullByDefault
public class IntroductionResponse extends PrivateResponse<Introduction> {

	public IntroductionResponse(MessageId messageId, GroupId groupId, long time,
			boolean local, boolean sent, boolean seen, boolean read,
			SessionId sessionId, Introduction introduction, boolean accepted) {
		super(messageId, groupId, time, local, sent, seen, read, sessionId,
				introduction, accepted);
	}

}
