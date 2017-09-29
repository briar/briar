package org.briarproject.briar.android;

import android.app.IntentService;
import android.content.Intent;
import android.support.annotation.Nullable;
import android.util.Log;

import org.briarproject.briar.api.android.AndroidNotificationManager;

import javax.inject.Inject;

import static org.briarproject.briar.api.android.AndroidNotificationManager.BLOG_URI;
import static org.briarproject.briar.api.android.AndroidNotificationManager.CONTACT_URI;
import static org.briarproject.briar.api.android.AndroidNotificationManager.FORUM_URI;
import static org.briarproject.briar.api.android.AndroidNotificationManager.GROUP_URI;
import static org.briarproject.briar.api.android.AndroidNotificationManager.INTRODUCTION_URI;

public class NotificationCleanupService extends IntentService {

	private static final String TAG =
			NotificationCleanupService.class.getName();

	@Inject
	AndroidNotificationManager notificationManager;

	public NotificationCleanupService() {
		super(TAG);
	}

	@Override
	public void onCreate() {
		super.onCreate();
		AndroidComponent applicationComponent =
				((BriarApplication) getApplication()).getApplicationComponent();
		applicationComponent.inject(this);
	}

	@Override
	protected void onHandleIntent(@Nullable Intent i) {
		if (i == null || i.getData() == null) {
			Log.i(TAG, "No intent or no data");
			return;
		}
		String uri = i.getData().toString();
		if (uri.equals(CONTACT_URI)) {
			Log.i(TAG, "Clearing contact notifications");
			notificationManager.clearAllContactNotifications();
		} else if (uri.equals(GROUP_URI)) {
			Log.i(TAG, "Clearing group notifications");
			notificationManager.clearAllGroupMessageNotifications();
		} else if (uri.equals(FORUM_URI)) {
			Log.i(TAG, "Clearing forum notifications");
			notificationManager.clearAllForumPostNotifications();
		} else if (uri.equals(BLOG_URI)) {
			Log.i(TAG, "Clearing blog notifications");
			notificationManager.clearAllBlogPostNotifications();
		} else if (uri.equals(INTRODUCTION_URI)) {
			Log.i(TAG, "Clearing introduction notifications");
			notificationManager.clearAllIntroductionNotifications();
		} else {
			Log.w(TAG, "Unknown intent URI: " + uri);
		}
	}
}
