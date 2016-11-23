package org.briarproject.briar.api.blog;

import org.briarproject.bramble.api.identity.Author;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.sync.Message;
import org.briarproject.bramble.api.sync.MessageId;
import org.briarproject.briar.api.forum.ForumPost;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

@Immutable
@NotNullByDefault
public class BlogPost extends ForumPost {

	public BlogPost(Message message, @Nullable MessageId parent,
			Author author) {
		super(message, parent, author);
	}
}
