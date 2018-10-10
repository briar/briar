package org.briarproject.briar.api.introduction;

import org.briarproject.bramble.api.identity.Author;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.sync.GroupId;
import org.briarproject.bramble.api.sync.MessageId;
import org.briarproject.briar.api.client.SessionId;
import org.briarproject.briar.api.messaging.PrivateMessageVisitor;
import org.briarproject.briar.api.messaging.PrivateRequest;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

@Immutable
@NotNullByDefault
public class IntroductionRequest extends PrivateRequest<Author> {

	private final boolean contact;

	public IntroductionRequest(MessageId messageId, GroupId groupId,
			long time, boolean local, boolean sent, boolean seen, boolean read,
			SessionId sessionId, Author author, @Nullable String text,
			boolean answered, boolean contact) {
		super(messageId, groupId, time, local, sent, seen, read, sessionId,
				author, text, answered);
		this.contact = contact;
	}

	public boolean isContact() {
		return contact;
	}

	@Override
	public <T> T accept(PrivateMessageVisitor<T> v) {
		return v.visitIntroductionRequest(this);
	}
}
