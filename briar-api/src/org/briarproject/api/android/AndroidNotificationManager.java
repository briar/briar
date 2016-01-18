package org.briarproject.api.android;

import org.briarproject.api.contact.ContactId;
import org.briarproject.api.lifecycle.Service;
import org.briarproject.api.sync.GroupId;

/**
 * Manages notifications for private messages and group posts. All methods must
 * be called from the Android UI thread.
 */
public interface AndroidNotificationManager extends Service {

	void showPrivateMessageNotification(ContactId c);

	void clearPrivateMessageNotification(ContactId c);

	void blockPrivateMessageNotification(ContactId c);

	void unblockPrivateMessageNotification(ContactId c);

	void showForumPostNotification(GroupId g);

	void clearForumPostNotification(GroupId g);

	void clearNotifications();
}
