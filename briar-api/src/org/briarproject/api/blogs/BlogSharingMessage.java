package org.briarproject.api.blogs;

import org.briarproject.api.FormatException;
import org.briarproject.api.clients.SessionId;
import org.briarproject.api.data.BdfDictionary;
import org.briarproject.api.data.BdfList;
import org.briarproject.api.sharing.SharingMessage.Invitation;
import org.briarproject.api.sync.GroupId;

import static org.briarproject.api.blogs.BlogConstants.BLOG_AUTHOR_NAME;
import static org.briarproject.api.blogs.BlogConstants.BLOG_DESC;
import static org.briarproject.api.blogs.BlogConstants.BLOG_PUBLIC_KEY;
import static org.briarproject.api.blogs.BlogConstants.BLOG_TITLE;
import static org.briarproject.api.sharing.SharingConstants.INVITATION_MSG;
import static org.briarproject.api.sharing.SharingConstants.SESSION_ID;
import static org.briarproject.api.sharing.SharingConstants.TIME;

public interface BlogSharingMessage {

	class BlogInvitation extends Invitation {

		private final String blogTitle;
		private final String blogDesc;
		private final String blogAuthorName;
		private final byte[] blogPublicKey;

		public BlogInvitation(GroupId groupId, SessionId sessionId,
				String blogTitle, String blogDesc, String blogAuthorName,
				byte[] blogPublicKey, long time, String message) {

			super(groupId, sessionId, time, message);

			this.blogTitle = blogTitle;
			this.blogDesc = blogDesc;
			this.blogAuthorName = blogAuthorName;
			this.blogPublicKey = blogPublicKey;
		}

		@Override
		public BdfList toBdfList() {
			BdfList list = super.toBdfList();
			list.add(blogTitle);
			list.add(blogDesc);
			list.add(BdfList.of(blogAuthorName, blogPublicKey));
			if (message != null) list.add(message);
			return list;
		}

		@Override
		public BdfDictionary toBdfDictionary() {
			BdfDictionary d = toBdfDictionaryHelper();
			d.put(BLOG_TITLE, blogTitle);
			d.put(BLOG_DESC, blogDesc);
			d.put(BLOG_AUTHOR_NAME, blogAuthorName);
			d.put(BLOG_PUBLIC_KEY, blogPublicKey);
			if (message != null) d.put(INVITATION_MSG, message);
			return d;
		}

		public static BlogInvitation from(GroupId groupId, BdfDictionary d)
				throws FormatException {

			SessionId sessionId = new SessionId(d.getRaw(SESSION_ID));
			String blogTitle = d.getString(BLOG_TITLE);
			String blogDesc = d.getString(BLOG_DESC);
			String blogAuthorName = d.getString(BLOG_AUTHOR_NAME);
			byte[] blogPublicKey = d.getRaw(BLOG_PUBLIC_KEY);
			String message = d.getOptionalString(INVITATION_MSG);
			long time = d.getLong(TIME);

			return new BlogInvitation(groupId, sessionId, blogTitle,
					blogDesc, blogAuthorName, blogPublicKey, time, message);
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
}
