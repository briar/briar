package org.briarproject.android.forum;

import android.support.annotation.Nullable;
import android.support.annotation.UiThread;

import org.briarproject.android.DestroyableContext;
import org.briarproject.android.controller.ActivityLifecycleController;
import org.briarproject.android.controller.handler.ResultExceptionHandler;
import org.briarproject.android.controller.handler.ResultHandler;
import org.briarproject.api.db.DbException;
import org.briarproject.api.forum.Forum;
import org.briarproject.api.forum.ForumPostHeader;
import org.briarproject.api.sync.GroupId;
import org.briarproject.api.sync.MessageId;

import java.util.Collection;
import java.util.List;

public interface ForumController extends ActivityLifecycleController {

	void loadForum(GroupId groupId,
			ResultExceptionHandler<List<ForumEntry>, DbException> resultHandler);

	@Nullable
	Forum getForum();

	void loadPost(ForumPostHeader header,
			ResultExceptionHandler<ForumEntry, DbException> resultHandler);

	void unsubscribe(ResultHandler<Boolean> resultHandler);

	void entryRead(ForumEntry forumEntry);

	void entriesRead(Collection<ForumEntry> messageIds);

	void createPost(byte[] body,
			ResultExceptionHandler<ForumEntry, DbException> resultHandler);

	void createPost(byte[] body, MessageId parentId,
			ResultExceptionHandler<ForumEntry, DbException> resultHandler);

	interface ForumPostListener extends DestroyableContext {

		@UiThread
		void onForumPostReceived(ForumPostHeader header);

		@UiThread
		void onForumRemoved();
	}

}
