package org.briarproject.android.forum;

import android.support.annotation.Nullable;

import org.briarproject.android.controller.ActivityLifecycleController;
import org.briarproject.android.controller.handler.ResultHandler;
import org.briarproject.android.controller.handler.UiResultHandler;
import org.briarproject.api.forum.Forum;
import org.briarproject.api.sync.GroupId;
import org.briarproject.api.sync.MessageId;

import java.util.Collection;
import java.util.List;

public interface ForumController extends ActivityLifecycleController {

	void loadForum(GroupId groupId, ResultHandler<Boolean> resultHandler);
	@Nullable
	Forum getForum();
	List<ForumEntry> getForumEntries();
	void unsubscribe(UiResultHandler<Boolean> resultHandler);
	void entryRead(ForumEntry forumEntry);
	void entriesRead(Collection<ForumEntry> messageIds);
	void createPost(byte[] body);
	void createPost(byte[] body, MessageId parentId);

	interface ForumPostListener {
		void addLocalEntry(int index, ForumEntry entry);
		void addForeignEntry(int index, ForumEntry entry);
	}

}
