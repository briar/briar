package org.briarproject.api.android;

import org.briarproject.api.lifecycle.Service;
import org.briarproject.api.sync.GroupId;

/** Manages notifications for private messages and forum posts. */
public interface AndroidNotificationManager extends Service {

	void showPrivateMessageNotification(GroupId g);

	void clearPrivateMessageNotification(GroupId g);

	void showForumPostNotification(GroupId g);

	void clearForumPostNotification(GroupId g);

	void blockNotification(GroupId g);

	void unblockNotification(GroupId g);
}
