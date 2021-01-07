package org.briarproject.briar.android.forum;

import org.briarproject.bramble.api.contact.ContactId;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.briar.android.threaded.ThreadListController;
import org.briarproject.briar.api.forum.Forum;

import androidx.annotation.UiThread;

@NotNullByDefault
interface ForumController extends ThreadListController<Forum, ForumPostItem> {

	interface ForumListener extends ThreadListListener<ForumPostItem> {
		@UiThread
		void onForumLeft(ContactId c);
	}

}
