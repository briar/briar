package org.briarproject.briar.api.introduction;

import org.briarproject.bramble.api.identity.Author;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.sync.GroupId;
import org.briarproject.bramble.api.sync.MessageId;
import org.briarproject.briar.api.client.SessionId;
import org.briarproject.briar.api.messaging.PrivateMessageVisitor;
import org.briarproject.briar.api.messaging.PrivateResponse;

import javax.annotation.concurrent.Immutable;

import static org.briarproject.briar.api.introduction.Role.INTRODUCER;

@Immutable
@NotNullByDefault
public class IntroductionResponse extends PrivateResponse {

	private final Author introducedAuthor;
	private final Role ourRole;

	public IntroductionResponse(MessageId messageId, GroupId groupId, long time,
			boolean local, boolean sent, boolean seen, boolean read,
			SessionId sessionId, boolean accepted, Author author, Role role) {
		super(messageId, groupId, time, local, sent, seen, read, sessionId,
				accepted);
		this.introducedAuthor = author;
		this.ourRole = role;
	}

	public Author getIntroducedAuthor() {
		return introducedAuthor;
	}

	public boolean isIntroducer() {
		return ourRole == INTRODUCER;
	}

	@Override
	public <T> T accept(PrivateMessageVisitor<T> v) {
		return v.visitIntroductionResponse(this);
	}
}
