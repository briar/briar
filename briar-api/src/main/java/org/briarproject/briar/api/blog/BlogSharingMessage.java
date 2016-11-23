package org.briarproject.briar.api.blog;

import org.briarproject.bramble.api.FormatException;
import org.briarproject.bramble.api.data.BdfDictionary;
import org.briarproject.bramble.api.data.BdfList;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.sync.GroupId;
import org.briarproject.briar.api.client.SessionId;
import org.briarproject.briar.api.sharing.SharingMessage.Invitation;

import javax.annotation.Nullable;

import static org.briarproject.briar.api.blog.BlogConstants.BLOG_AUTHOR_NAME;
import static org.briarproject.briar.api.blog.BlogConstants.BLOG_PUBLIC_KEY;
import static org.briarproject.briar.api.sharing.SharingConstants.INVITATION_MSG;
import static org.briarproject.briar.api.sharing.SharingConstants.SESSION_ID;
import static org.briarproject.briar.api.sharing.SharingConstants.TIME;

@NotNullByDefault
public interface BlogSharingMessage {

	class BlogInvitation extends Invitation {

		private final String blogAuthorName;
		private final byte[] blogPublicKey;

		public BlogInvitation(GroupId groupId, SessionId sessionId,
				String blogAuthorName, byte[] blogPublicKey, long time,
				@Nullable String message) {
			super(groupId, sessionId, time, message);

			this.blogAuthorName = blogAuthorName;
			this.blogPublicKey = blogPublicKey;
		}

		@Override
		public BdfList toBdfList() {
			BdfList list = super.toBdfList();
			list.add(BdfList.of(blogAuthorName, blogPublicKey));
			if (message != null) list.add(message);
			return list;
		}

		@Override
		public BdfDictionary toBdfDictionary() {
			BdfDictionary d = toBdfDictionaryHelper();
			d.put(BLOG_AUTHOR_NAME, blogAuthorName);
			d.put(BLOG_PUBLIC_KEY, blogPublicKey);
			if (message != null) d.put(INVITATION_MSG, message);
			return d;
		}

		public static BlogInvitation from(GroupId groupId, BdfDictionary d)
				throws FormatException {

			SessionId sessionId = new SessionId(d.getRaw(SESSION_ID));
			String blogAuthorName = d.getString(BLOG_AUTHOR_NAME);
			byte[] blogPublicKey = d.getRaw(BLOG_PUBLIC_KEY);
			String message = d.getOptionalString(INVITATION_MSG);
			long time = d.getLong(TIME);

			return new BlogInvitation(groupId, sessionId, blogAuthorName,
					blogPublicKey, time, message);
		}

		public String getBlogAuthorName() {
			return blogAuthorName;
		}

		public byte[] getBlogPublicKey() {
			return blogPublicKey;
		}
	}
}
