package org.briarproject.briar.api.android;

import org.briarproject.bramble.api.contact.ContactId;
import org.briarproject.bramble.api.sync.GroupId;

/**
 * Manages notifications for private messages, forum posts, blog posts and
 * introductions.
 */
public interface AndroidNotificationManager {

	// Keys for notification preferences
	String PREF_NOTIFY_PRIVATE = "notifyPrivateMessages";
	String PREF_NOTIFY_GROUP = "notifyGroupMessages";
	String PREF_NOTIFY_FORUM = "notifyForumPosts";
	String PREF_NOTIFY_BLOG = "notifyBlogPosts";

	String PREF_NOTIFY_SOUND = "notifySound";
	String PREF_NOTIFY_RINGTONE_NAME = "notifyRingtoneName";
	String PREF_NOTIFY_RINGTONE_URI = "notifyRingtoneUri";
	String PREF_NOTIFY_VIBRATION = "notifyVibration";
	String PREF_NOTIFY_LOCK_SCREEN = "notifyLockScreen";

	// Notification IDs
	int ONGOING_NOTIFICATION_ID = 1;
	int FAILURE_NOTIFICATION_ID = 2;
	int REMINDER_NOTIFICATION_ID = 3;
	int PRIVATE_MESSAGE_NOTIFICATION_ID = 4;
	int GROUP_MESSAGE_NOTIFICATION_ID = 5;
	int FORUM_POST_NOTIFICATION_ID = 6;
	int BLOG_POST_NOTIFICATION_ID = 7;
	int INTRODUCTION_SUCCESS_NOTIFICATION_ID = 8;

	// Channel IDs
	String CONTACT_CHANNEL_ID = "contacts";
	String GROUP_CHANNEL_ID = "groups";
	String FORUM_CHANNEL_ID = "forums";
	String BLOG_CHANNEL_ID = "blogs";
	// Channels are sorted by channel ID in the Settings app, so use IDs
	// that will sort below the main channels such as contacts
	String ONGOING_CHANNEL_ID = "zForegroundService";
	String FAILURE_CHANNEL_ID = "zStartupFailure";
	String REMINDER_CHANNEL_ID = "zSignInReminder";

	// Content URIs for pending intents
	String CONTACT_URI = "content://org.briarproject.briar/contact";
	String GROUP_URI = "content://org.briarproject.briar/group";
	String FORUM_URI = "content://org.briarproject.briar/forum";
	String BLOG_URI = "content://org.briarproject.briar/blog";
	String INTRODUCTION_URI = "content://org.briarproject.briar/introduction";

	void clearContactNotification(ContactId c);

	void clearAllContactNotifications();

	void clearGroupMessageNotification(GroupId g);

	void clearAllGroupMessageNotifications();

	void clearForumPostNotification(GroupId g);

	void clearAllForumPostNotifications();

	void clearBlogPostNotification(GroupId g);

	void clearAllBlogPostNotifications();

	void clearAllIntroductionNotifications();

	void blockContactNotification(ContactId c);

	void unblockContactNotification(ContactId c);

	void blockNotification(GroupId g);

	void unblockNotification(GroupId g);

	void blockAllBlogPostNotifications();

	void unblockAllBlogPostNotifications();
}
