package org.briarproject.briar.android;

import android.app.IntentService;
import android.content.Intent;
import android.support.annotation.Nullable;

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
		if (i == null || i.getData() == null) return;
		String uri = i.getData().toString();
		if (uri.equals(CONTACT_URI)) {
			notificationManager.clearAllContactNotifications();
		} else if (uri.equals(GROUP_URI)) {
			notificationManager.clearAllGroupMessageNotifications();
		} else if (uri.equals(FORUM_URI)) {
			notificationManager.clearAllForumPostNotifications();
		} else if (uri.equals(BLOG_URI)) {
			notificationManager.clearAllBlogPostNotifications();
		} else if (uri.equals(INTRODUCTION_URI)) {
			notificationManager.clearAllIntroductionNotifications();
		}
	}
}
