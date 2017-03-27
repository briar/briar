package org.briarproject.briar.api.android;

import org.briarproject.bramble.api.contact.ContactId;
import org.briarproject.bramble.api.sync.GroupId;

/**
 * Manages notifications for private messages, forum posts, blog posts and
 * introductions.
 */
public interface AndroidNotificationManager {

	String PREF_NOTIFY_PRIVATE = "notifyPrivateMessages";
	String PREF_NOTIFY_GROUP = "notifyGroupMessages";
	String PREF_NOTIFY_FORUM = "notifyForumPosts";
	String PREF_NOTIFY_BLOG = "notifyBlogPosts";

	String PREF_NOTIFY_SOUND = "notifySound";
	String PREF_NOTIFY_RINGTONE_NAME = "notifyRingtoneName";
	String PREF_NOTIFY_RINGTONE_URI = "notifyRingtoneUri";
	String PREF_NOTIFY_VIBRATION = "notifyVibration";

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
