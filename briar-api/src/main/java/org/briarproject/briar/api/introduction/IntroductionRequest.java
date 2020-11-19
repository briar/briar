package org.briarproject.briar.api.introduction;

import org.briarproject.bramble.api.identity.Author;
import org.briarproject.briar.api.identity.AuthorInfo;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.sync.GroupId;
import org.briarproject.bramble.api.sync.MessageId;
import org.briarproject.briar.api.client.SessionId;
import org.briarproject.briar.api.conversation.ConversationMessageVisitor;
import org.briarproject.briar.api.conversation.ConversationRequest;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

@Immutable
@NotNullByDefault
public class IntroductionRequest extends ConversationRequest<Author> {

	private final AuthorInfo authorInfo;

	public IntroductionRequest(MessageId messageId, GroupId groupId, long time,
			boolean local, boolean read, boolean sent, boolean seen,
			SessionId sessionId, Author author, @Nullable String text,
			boolean answered, AuthorInfo authorInfo, long autoDeleteTimer) {
		super(messageId, groupId, time, local, read, sent, seen, sessionId,
				author, text, answered, autoDeleteTimer);
		this.authorInfo = authorInfo;
	}

	@Nullable
	public String getAlias() {
		return authorInfo.getAlias();
	}

	public boolean isContact() {
		return authorInfo.getStatus().isContact();
	}

	@Override
	public <T> T accept(ConversationMessageVisitor<T> v) {
		return v.visitIntroductionRequest(this);
	}
}
