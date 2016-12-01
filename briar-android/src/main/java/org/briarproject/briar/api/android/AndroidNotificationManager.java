package org.briarproject.briar.api.android;

import org.briarproject.bramble.api.contact.ContactId;
import org.briarproject.bramble.api.sync.GroupId;

/**
 * Manages notifications for private messages, forum posts, blog posts and
 * introductions.
 */
public interface AndroidNotificationManager {

	void clearContactNotification(ContactId c);

	void clearAllContactNotifications();

	void clearGroupMessageNotification(GroupId g);

	void clearAllGroupMessageNotifications();

	void clearForumPostNotification(GroupId g);

	void clearAllForumPostNotifications();

	void clearBlogPostNotification(GroupId g);

	void clearAllBlogPostNotifications();

	void blockContactNotification(ContactId c);

	void unblockContactNotification(ContactId c);

	void blockNotification(GroupId g);

	void unblockNotification(GroupId g);

	void blockAllContactNotifications();

	void unblockAllContactNotifications();

	void blockAllGroupMessageNotifications();

	void unblockAllGroupMessageNotifications();

	void blockAllForumPostNotifications();

	void unblockAllForumPostNotifications();

	void blockAllBlogPostNotifications();

	void unblockAllBlogPostNotifications();
}
