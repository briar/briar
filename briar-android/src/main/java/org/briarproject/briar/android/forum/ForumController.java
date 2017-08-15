package org.briarproject.briar.android.forum;

import android.support.annotation.UiThread;

import org.briarproject.bramble.api.contact.ContactId;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.briar.android.threaded.ThreadListController;
import org.briarproject.briar.api.forum.Forum;

@NotNullByDefault
interface ForumController extends ThreadListController<Forum, ForumItem> {

	interface ForumListener extends ThreadListListener<ForumItem> {
		@UiThread
		void onForumLeft(ContactId c);
	}

}
