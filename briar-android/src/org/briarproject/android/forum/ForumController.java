package org.briarproject.android.forum;

import org.briarproject.android.controller.ActivityLifecycleController;
import org.briarproject.android.controller.handler.UiResultHandler;
import org.briarproject.api.sync.GroupId;
import org.briarproject.api.sync.MessageId;

import java.util.Collection;
import java.util.List;

public interface ForumController extends ActivityLifecycleController {

	void loadForum(GroupId groupId, UiResultHandler<Boolean> resultHandler);
	String getForumName();
	List<ForumEntry> getForumEntries();
	void unsubscribe(UiResultHandler<Boolean> resultHandler);
	void entryRead(ForumEntry forumEntry);
	void entriesRead(Collection<ForumEntry> messageIds);
	void createPost(byte[] body);
	void createPost(byte[] body, MessageId parentId);

	public interface ForumPostListener {
		void addLocalEntry(int index, ForumEntry entry);
		void addForeignEntry(int index, ForumEntry entry);
	}

}
