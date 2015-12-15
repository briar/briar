package org.briarproject.api.android;

import org.briarproject.api.ContactId;
import org.briarproject.api.lifecycle.Service;
import org.briarproject.api.messaging.GroupId;

/**
 * Manages notifications for private messages and group posts. All methods must
 * be called from the Android UI thread.
 */
public interface AndroidNotificationManager extends Service {

	public void showPrivateMessageNotification(ContactId c);

	public void clearPrivateMessageNotification(ContactId c);

	public void showGroupPostNotification(GroupId g);

	public void clearGroupPostNotification(GroupId g);

	public void clearNotifications();
}
