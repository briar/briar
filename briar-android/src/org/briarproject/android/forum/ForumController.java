package org.briarproject.android.forum;

import android.support.annotation.Nullable;
import android.support.annotation.UiThread;

import org.briarproject.android.controller.ActivityLifecycleController;
import org.briarproject.android.controller.handler.ResultHandler;
import org.briarproject.api.forum.Forum;
import org.briarproject.api.forum.ForumPost;
import org.briarproject.api.forum.ForumPostHeader;
import org.briarproject.api.sync.GroupId;
import org.briarproject.api.sync.MessageId;

import java.util.Collection;
import java.util.List;

public interface ForumController extends ActivityLifecycleController {

	void loadForum(GroupId groupId,
			ResultHandler<List<ForumEntry>> resultHandler);

	@Nullable
	Forum getForum();

	void loadPost(ForumPostHeader header,
			ResultHandler<ForumEntry> resultHandler);

	void unsubscribe(ResultHandler<Boolean> resultHandler);

	void entryRead(ForumEntry forumEntry);

	void entriesRead(Collection<ForumEntry> messageIds);

	void createPost(byte[] body, ResultHandler<ForumPost> resultHandler);

	void createPost(byte[] body, MessageId parentId,
			ResultHandler<ForumPost> resultHandler);

	void storePost(ForumPost post, ResultHandler<ForumEntry> resultHandler);

	interface ForumPostListener {
		@UiThread
		void onExternalEntryAdded(ForumPostHeader header);
	}

}
