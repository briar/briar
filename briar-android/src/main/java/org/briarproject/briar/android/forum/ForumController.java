package org.briarproject.briar.android.forum;

import org.briarproject.bramble.api.contact.ContactId;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.briar.android.threaded.ThreadListController;

import androidx.annotation.UiThread;

@NotNullByDefault
interface ForumController extends ThreadListController<ForumPostItem> {

	interface ForumListener extends ThreadListListener<ForumPostItem> {
		@UiThread
		void onForumLeft(ContactId c);
	}

}
