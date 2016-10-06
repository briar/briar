package org.briarproject.sharing;

import org.briarproject.api.clients.SessionId;
import org.briarproject.api.contact.ContactId;
import org.briarproject.api.data.BdfDictionary;
import org.briarproject.api.sync.GroupId;
import org.briarproject.api.sync.MessageId;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static org.briarproject.api.blogs.BlogConstants.BLOG_AUTHOR_NAME;
import static org.briarproject.api.blogs.BlogConstants.BLOG_DESC;
import static org.briarproject.api.blogs.BlogConstants.BLOG_PUBLIC_KEY;
import static org.briarproject.api.blogs.BlogConstants.BLOG_TITLE;

public class BlogInviteeSessionState extends InviteeSessionState {

	private final String blogTitle;
	private final String blogDesc;
	private final String blogAuthorName;
	private final byte[] blogPublicKey;

	public BlogInviteeSessionState(SessionId sessionId, MessageId storageId,
			GroupId groupId, State state, ContactId contactId, GroupId blogId,
			String blogTitle, String blogDesc, String blogAuthorName,
			byte[] blogPublicKey, @NotNull MessageId invitationId) {
		super(sessionId, storageId, groupId, state, contactId, blogId,
				invitationId);

		this.blogTitle = blogTitle;
		this.blogDesc = blogDesc;
		this.blogAuthorName = blogAuthorName;
		this.blogPublicKey = blogPublicKey;
	}

	public BdfDictionary toBdfDictionary() {
		BdfDictionary d = super.toBdfDictionary();
		d.put(BLOG_TITLE, getBlogTitle());
		d.put(BLOG_DESC, getBlogDesc());
		d.put(BLOG_AUTHOR_NAME, getBlogAuthorName());
		d.put(BLOG_PUBLIC_KEY, getBlogPublicKey());
		return d;
	}

	public String getBlogTitle() {
		return blogTitle;
	}

	public String getBlogDesc() {
		return blogDesc;
	}

	public String getBlogAuthorName() {
		return blogAuthorName;
	}

	public byte[] getBlogPublicKey() {
		return blogPublicKey;
	}
}
