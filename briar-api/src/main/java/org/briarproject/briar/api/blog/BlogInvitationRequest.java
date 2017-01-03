package org.briarproject.briar.api.blog;

import org.briarproject.bramble.api.contact.ContactId;
import org.briarproject.bramble.api.identity.Author;
import org.briarproject.bramble.api.identity.AuthorId;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.sync.Group;
import org.briarproject.bramble.api.sync.GroupId;
import org.briarproject.bramble.api.sync.MessageId;
import org.briarproject.briar.api.client.SessionId;
import org.briarproject.briar.api.sharing.InvitationRequest;

import javax.annotation.Nullable;

@NotNullByDefault
public class BlogInvitationRequest extends InvitationRequest<Blog> {

	private final String blogAuthorName;

	public BlogInvitationRequest(MessageId id, SessionId sessionId,
			GroupId groupId, ContactId contactId, String blogAuthorName,
			@Nullable String message, GroupId blogId,
			boolean available, boolean canBeOpened, long time,
			boolean local, boolean sent, boolean seen, boolean read) {
		// TODO pass a proper blog here when redoing the BlogSharingManager
		super(id, groupId, time, local, sent, seen, read, sessionId,
				new Blog(new Group(blogId, BlogManager.CLIENT_ID, new byte[0]),
						new Author(new AuthorId(new byte[AuthorId.LENGTH]),
								blogAuthorName, new byte[0])), contactId,
				message, available, canBeOpened);
		this.blogAuthorName = blogAuthorName;
	}

	public String getBlogAuthorName() {
		return blogAuthorName;
	}

}
