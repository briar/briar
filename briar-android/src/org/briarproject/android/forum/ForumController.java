package org.briarproject.android.forum;

import org.briarproject.android.controller.handler.ResultExceptionHandler;
import org.briarproject.android.threaded.ThreadListController;
import org.briarproject.api.db.DbException;
import org.briarproject.api.forum.Forum;
import org.briarproject.api.forum.ForumPostHeader;

public interface ForumController
		extends ThreadListController<Forum, ForumEntry, ForumPostHeader> {

}
