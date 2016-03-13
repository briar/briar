package org.briarproject.android.api;

import org.briarproject.api.sync.GroupId;

/** Manages notifications for private messages and forum posts. */
public interface AndroidNotificationManager {

	void showPrivateMessageNotification(GroupId g);

	void clearPrivateMessageNotification(GroupId g);

	void showForumPostNotification(GroupId g);

	void clearForumPostNotification(GroupId g);

	void blockNotification(GroupId g);

	void unblockNotification(GroupId g);
}
