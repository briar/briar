package org.briarproject.briar.sharing;

import org.briarproject.bramble.api.contact.ContactId;
import org.briarproject.bramble.api.data.BdfDictionary;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.sync.GroupId;
import org.briarproject.bramble.api.sync.MessageId;
import org.briarproject.briar.api.client.SessionId;

import javax.annotation.concurrent.NotThreadSafe;

import static org.briarproject.briar.api.blog.BlogConstants.BLOG_AUTHOR_NAME;
import static org.briarproject.briar.api.blog.BlogConstants.BLOG_PUBLIC_KEY;

@NotThreadSafe
@NotNullByDefault
class BlogInviteeSessionState extends InviteeSessionState {

	private final String blogAuthorName;
	private final byte[] blogPublicKey;

	BlogInviteeSessionState(SessionId sessionId, MessageId storageId,
			GroupId groupId, State state, ContactId contactId, GroupId blogId,
			String blogAuthorName, byte[] blogPublicKey,
			MessageId invitationId) {
		super(sessionId, storageId, groupId, state, contactId, blogId,
				invitationId);
		this.blogAuthorName = blogAuthorName;
		this.blogPublicKey = blogPublicKey;
	}

	@Override
	public BdfDictionary toBdfDictionary() {
		BdfDictionary d = super.toBdfDictionary();
		d.put(BLOG_AUTHOR_NAME, getBlogAuthorName());
		d.put(BLOG_PUBLIC_KEY, getBlogPublicKey());
		return d;
	}

	String getBlogAuthorName() {
		return blogAuthorName;
	}

	byte[] getBlogPublicKey() {
		return blogPublicKey;
	}
}
