package org.briarproject.briar.api.forum;

import org.briarproject.bramble.api.identity.Author;
import org.briarproject.bramble.api.sync.MessageId;
import org.briarproject.briar.api.client.PostHeader;
import org.briarproject.briar.api.identity.AuthorInfo;
import org.briarproject.nullsafety.NotNullByDefault;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

@Immutable
@NotNullByDefault
public class ForumPostHeader extends PostHeader {

	public ForumPostHeader(MessageId id, @Nullable MessageId parentId,
			long timestamp, Author author, AuthorInfo authorInfo,
			boolean read) {
		super(id, parentId, timestamp, author, authorInfo, read);
	}

}
