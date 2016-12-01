package org.briarproject.briar.android.forum;

import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.briar.android.threaded.ThreadListController;
import org.briarproject.briar.api.forum.Forum;
import org.briarproject.briar.api.forum.ForumPostHeader;

@NotNullByDefault
interface ForumController
		extends ThreadListController<Forum, ForumItem, ForumPostHeader> {

}
