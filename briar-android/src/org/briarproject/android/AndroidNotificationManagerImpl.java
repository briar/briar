package org.briarproject.android;

import static android.app.Notification.DEFAULT_ALL;
import static android.content.Context.NOTIFICATION_SERVICE;
import static android.content.Intent.FLAG_ACTIVITY_NEW_TASK;
import static android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP;

import java.util.HashMap;
import java.util.Map;

import javax.inject.Inject;

import org.briarproject.R;
import org.briarproject.android.contact.ContactListActivity;
import org.briarproject.android.contact.ConversationActivity;
import org.briarproject.android.groups.GroupActivity;
import org.briarproject.android.groups.GroupListActivity;
import org.briarproject.api.ContactId;
import org.briarproject.api.android.AndroidNotificationManager;
import org.briarproject.api.messaging.GroupId;

import android.app.Application;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.TaskStackBuilder;

class AndroidNotificationManagerImpl implements AndroidNotificationManager {

	private static final int PRIVATE_MESSAGE_NOTIFICATION_ID = 3;
	private static final int GROUP_POST_NOTIFICATION_ID = 4;

	private final Context appContext;
	private final Map<ContactId, Integer> contactCounts =
			new HashMap<ContactId, Integer>(); // Locking: this
	private final Map<GroupId, Integer> groupCounts =
			new HashMap<GroupId, Integer>(); // Locking: this

	private int privateTotal = 0, groupTotal = 0; // Locking: this

	@Inject
	public AndroidNotificationManagerImpl(Application app) {
		this.appContext = app.getApplicationContext();
	}

	public synchronized void showPrivateMessageNotification(ContactId c) {
		Integer count = contactCounts.get(c);
		if(count == null) contactCounts.put(c, 1);
		else contactCounts.put(c, count + 1);
		privateTotal++;
		updatePrivateMessageNotification();
	}

	public synchronized void clearPrivateMessageNotification(ContactId c) {
		Integer count = contactCounts.remove(c);
		if(count == null) return; // Already cleared
		privateTotal -= count;
		updatePrivateMessageNotification();
	}

	// Locking: this
	private void updatePrivateMessageNotification() {
		if(privateTotal == 0) {
			clearPrivateMessageNotification();
		} else {
			NotificationCompat.Builder b =
					new NotificationCompat.Builder(appContext);
			b.setSmallIcon(R.drawable.message_notification_icon);
			b.setContentTitle(appContext.getText(R.string.app_name));
			b.setContentText(appContext.getResources().getQuantityString(
					R.plurals.private_message_notification_text, privateTotal,
					privateTotal));
			b.setDefaults(DEFAULT_ALL);
			b.setOnlyAlertOnce(true);
			if(contactCounts.size() == 1) {
				Intent i = new Intent(appContext, ConversationActivity.class);
				ContactId c = contactCounts.keySet().iterator().next();
				i.putExtra("briar.CONTACT_ID", c.getInt());
				i.setFlags(FLAG_ACTIVITY_NEW_TASK | FLAG_ACTIVITY_SINGLE_TOP);
				TaskStackBuilder tsb = TaskStackBuilder.create(appContext);
				tsb.addParentStack(ConversationActivity.class);
				tsb.addNextIntent(i);
				b.setContentIntent(tsb.getPendingIntent(0, 0));
			} else {
				Intent i = new Intent(appContext, ContactListActivity.class);
				i.setFlags(FLAG_ACTIVITY_NEW_TASK | FLAG_ACTIVITY_SINGLE_TOP);
				TaskStackBuilder tsb = TaskStackBuilder.create(appContext);
				tsb.addParentStack(ContactListActivity.class);
				tsb.addNextIntent(i);
				b.setContentIntent(tsb.getPendingIntent(0, 0));
			}
			Object o = appContext.getSystemService(NOTIFICATION_SERVICE);
			NotificationManager nm = (NotificationManager) o;
			nm.notify(PRIVATE_MESSAGE_NOTIFICATION_ID, b.build());
		}
	}

	// Locking: this
	private void clearPrivateMessageNotification() {
		Object o = appContext.getSystemService(NOTIFICATION_SERVICE);
		NotificationManager nm = (NotificationManager) o;
		nm.cancel(PRIVATE_MESSAGE_NOTIFICATION_ID);
	}

	public synchronized void showGroupPostNotification(GroupId g) {
		Integer count = groupCounts.get(g);
		if(count == null) groupCounts.put(g, 1);
		else groupCounts.put(g, count + 1);
		groupTotal++;
		updateGroupPostNotification();
	}

	public synchronized void clearGroupPostNotification(GroupId g) {
		Integer count = groupCounts.remove(g);
		if(count == null) return; // Already cleared
		groupTotal -= count;
		updateGroupPostNotification();
	}

	// Locking: this
	private void updateGroupPostNotification() {
		if(groupTotal == 0) {
			clearGroupPostNotification();
		} else {
			NotificationCompat.Builder b =
					new NotificationCompat.Builder(appContext);
			b.setSmallIcon(R.drawable.message_notification_icon);
			b.setContentTitle(appContext.getText(R.string.app_name));
			b.setContentText(appContext.getResources().getQuantityString(
					R.plurals.group_post_notification_text, groupTotal,
					groupTotal));
			b.setDefaults(DEFAULT_ALL);
			b.setOnlyAlertOnce(true);
			if(groupCounts.size() == 1) {
				Intent i = new Intent(appContext, GroupActivity.class);
				GroupId g = groupCounts.keySet().iterator().next();
				i.putExtra("briar.GROUP_ID", g.getBytes());
				i.setFlags(FLAG_ACTIVITY_NEW_TASK | FLAG_ACTIVITY_SINGLE_TOP);
				TaskStackBuilder tsb = TaskStackBuilder.create(appContext);
				tsb.addParentStack(GroupActivity.class);
				tsb.addNextIntent(i);
				b.setContentIntent(tsb.getPendingIntent(0, 0));
			} else {
				Intent i = new Intent(appContext, GroupListActivity.class);
				i.setFlags(FLAG_ACTIVITY_NEW_TASK | FLAG_ACTIVITY_SINGLE_TOP);
				TaskStackBuilder tsb = TaskStackBuilder.create(appContext);
				tsb.addParentStack(GroupListActivity.class);
				tsb.addNextIntent(i);
				b.setContentIntent(tsb.getPendingIntent(0, 0));
			}
			Object o = appContext.getSystemService(NOTIFICATION_SERVICE);
			NotificationManager nm = (NotificationManager) o;
			nm.notify(GROUP_POST_NOTIFICATION_ID, b.build());
		}
	}

	// Locking: this
	private void clearGroupPostNotification() {
		Object o = appContext.getSystemService(NOTIFICATION_SERVICE);
		NotificationManager nm = (NotificationManager) o;
		nm.cancel(GROUP_POST_NOTIFICATION_ID);
	}

	public synchronized void clearNotifications() {
		contactCounts.clear();
		groupCounts.clear();
		privateTotal = groupTotal = 0;
		clearPrivateMessageNotification();
		clearGroupPostNotification();
	}
}
