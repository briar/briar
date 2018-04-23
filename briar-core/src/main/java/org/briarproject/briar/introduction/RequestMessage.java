package org.briarproject.briar.introduction;

import org.briarproject.bramble.api.identity.Author;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.sync.GroupId;
import org.briarproject.bramble.api.sync.MessageId;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

@Immutable
@NotNullByDefault
class RequestMessage extends AbstractIntroductionMessage {

	private final Author author;
	@Nullable
	private final String message;

	protected RequestMessage(MessageId messageId, GroupId groupId,
			long timestamp, @Nullable MessageId previousMessageId,
			Author author, @Nullable String message) {
		super(messageId, groupId, timestamp, previousMessageId);
		this.author = author;
		this.message = message;
	}

	public Author getAuthor() {
		return author;
	}

	@Nullable
	public String getMessage() {
		return message;
	}

}
