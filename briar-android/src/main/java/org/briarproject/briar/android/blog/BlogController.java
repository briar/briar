package org.briarproject.briar.android.blog;

import org.briarproject.bramble.api.contact.ContactId;
import org.briarproject.bramble.api.db.DbException;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.sync.GroupId;
import org.briarproject.bramble.api.sync.MessageId;
import org.briarproject.briar.android.controller.handler.ResultExceptionHandler;

import java.util.Collection;
import java.util.List;

import androidx.annotation.UiThread;

@NotNullByDefault
public interface BlogController extends BaseController {

	void setGroupId(GroupId g);

	@UiThread
	void setBlogSharingListener(BlogSharingListener listener);

	@UiThread
	void unsetBlogSharingListener(BlogSharingListener listener);

	void loadBlogPosts(
			ResultExceptionHandler<List<BlogPostItem>, DbException> handler);

	void loadBlogPost(MessageId m,
			ResultExceptionHandler<BlogPostItem, DbException> handler);

	void loadBlog(ResultExceptionHandler<BlogItem, DbException> handler);

	void deleteBlog(ResultExceptionHandler<Void, DbException> handler);

	void loadSharingContacts(
			ResultExceptionHandler<Collection<ContactId>, DbException> handler);

	interface BlogSharingListener extends BlogListener {
		@UiThread
		void onBlogInvitationAccepted(ContactId c);

		@UiThread
		void onBlogLeft(ContactId c);
	}

}
