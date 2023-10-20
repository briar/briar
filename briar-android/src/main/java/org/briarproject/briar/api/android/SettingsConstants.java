package org.briarproject.briar.api.android;

import org.briarproject.nullsafety.NotNullByDefault;

@NotNullByDefault
public interface SettingsConstants {

	// Namespace for Android-specific settings
	String SETTINGS_NAMESPACE = "android-ui";

	// Key for time when expiry warning was last shown
	String EXPIRY_DATE_WARNING = "expiryDateWarning";

	// Key for doze mode exemption warning
	String DOZE_ASK_AGAIN = "dozeAskAgain";

	// Keys for onboarding
	String SHOW_ONBOARDING_TRANSPORTS = "showTransportsOnboarding";
	String SHOW_ONBOARDING_IMAGE = "showOnboardingImage";
	String SHOW_ONBOARDING_INTRODUCTION = "showOnboardingIntroduction";
	String SHOW_ONBOARDING_REVEAL_CONTACTS = "showOnboardingRevealContacts";

	// Keys for notification preferences
	String PREF_NOTIFY_PRIVATE = "notifyPrivateMessages";
	String PREF_NOTIFY_GROUP = "notifyGroupMessages";
	String PREF_NOTIFY_FORUM = "notifyForumPosts";
	String PREF_NOTIFY_BLOG = "notifyBlogPosts";
	String PREF_NOTIFY_SOUND = "notifySound";
	String PREF_NOTIFY_RINGTONE_NAME = "notifyRingtoneName";
	String PREF_NOTIFY_RINGTONE_URI = "notifyRingtoneUri";
	String PREF_NOTIFY_VIBRATION = "notifyVibration";

	// Key for recently used emoji
	String EMOJI_LRU_PREFERENCE = "pref_emoji_recent2";

	// Keys for screen lock
	String PREF_SCREEN_LOCK = "pref_key_lock";
	String PREF_SCREEN_LOCK_TIMEOUT = "pref_key_lock_timeout";
}
