package org.briarproject.android;

import static android.app.Notification.DEFAULT_LIGHTS;
import static android.app.Notification.DEFAULT_SOUND;
import static android.app.PendingIntent.FLAG_UPDATE_CURRENT;
import static android.content.Context.NOTIFICATION_SERVICE;
import static android.content.Intent.FLAG_ACTIVITY_NEW_TASK;
import static android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP;
import static java.util.logging.Level.WARNING;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.logging.Logger;

import javax.inject.Inject;

import org.briarproject.R;
import org.briarproject.android.contact.ContactListActivity;
import org.briarproject.android.contact.ConversationActivity;
import org.briarproject.android.groups.GroupActivity;
import org.briarproject.android.groups.GroupListActivity;
import org.briarproject.api.ContactId;
import org.briarproject.api.Settings;
import org.briarproject.api.android.AndroidNotificationManager;
import org.briarproject.api.android.DatabaseUiExecutor;
import org.briarproject.api.db.DatabaseComponent;
import org.briarproject.api.db.DbException;
import org.briarproject.api.event.Event;
import org.briarproject.api.event.EventListener;
import org.briarproject.api.event.SettingsUpdatedEvent;
import org.briarproject.api.lifecycle.Service;
import org.briarproject.api.messaging.GroupId;

import android.app.Application;
import android.app.Notification;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.TaskStackBuilder;

class AndroidNotificationManagerImpl implements AndroidNotificationManager,
Service, EventListener {

	private static final int PRIVATE_MESSAGE_NOTIFICATION_ID = 3;
	private static final int GROUP_POST_NOTIFICATION_ID = 4;

	private static final Logger LOG =
			Logger.getLogger(AndroidNotificationManagerImpl.class.getName());

	private final DatabaseComponent db;
	private final Executor dbUiExecutor;
	private final Context appContext;
	private final Map<ContactId, Integer> contactCounts =
			new HashMap<ContactId, Integer>(); // Locking: this
	private final Map<GroupId, Integer> groupCounts =
			new HashMap<GroupId, Integer>(); // Locking: this

	private int privateTotal = 0, groupTotal = 0; // Locking: this

	private volatile Settings settings = new Settings();

	@Inject
	public AndroidNotificationManagerImpl(DatabaseComponent db,
			@DatabaseUiExecutor Executor dbExecutor, Application app) {
		this.db = db;
		this.dbUiExecutor = dbExecutor;
		appContext = app.getApplicationContext();
	}

	public boolean start() {
		db.addListener(this);
		loadSettings();
		return true;
	}

	private void loadSettings() {
		dbUiExecutor.execute(new Runnable() {
			public void run() {
				try {
					settings = db.getSettings();
				} catch(DbException e) {
					if(LOG.isLoggable(WARNING))
						LOG.log(WARNING, e.toString(), e);
				}
			}
		});
	}

	public boolean stop() {
		db.removeListener(this);
		return true;
	}

	public void eventOccurred(Event e) {
		if(e instanceof SettingsUpdatedEvent) loadSettings();
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
		} else if(!settings.getBoolean("notifyPrivateMessages", true)) {
			return;
		} else {
			NotificationCompat.Builder b =
					new NotificationCompat.Builder(appContext);
			b.setSmallIcon(R.drawable.message_notification_icon);
			b.setContentTitle(appContext.getText(R.string.app_name));
			b.setContentText(appContext.getResources().getQuantityString(
					R.plurals.private_message_notification_text, privateTotal,
					privateTotal));
			b.setDefaults(getDefaults());
			b.setOnlyAlertOnce(true);
			if(contactCounts.size() == 1) {
				Intent i = new Intent(appContext, ConversationActivity.class);
				ContactId c = contactCounts.keySet().iterator().next();
				i.putExtra("briar.CONTACT_ID", c.getInt());
				i.setFlags(FLAG_ACTIVITY_NEW_TASK | FLAG_ACTIVITY_SINGLE_TOP);
				TaskStackBuilder t = TaskStackBuilder.create(appContext);
				t.addParentStack(ConversationActivity.class);
				t.addNextIntent(i);
				b.setContentIntent(t.getPendingIntent(0, FLAG_UPDATE_CURRENT));
			} else {
				Intent i = new Intent(appContext, ContactListActivity.class);
				i.setFlags(FLAG_ACTIVITY_NEW_TASK | FLAG_ACTIVITY_SINGLE_TOP);
				TaskStackBuilder t = TaskStackBuilder.create(appContext);
				t.addParentStack(ContactListActivity.class);
				t.addNextIntent(i);
				b.setContentIntent(t.getPendingIntent(0, FLAG_UPDATE_CURRENT));
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

	private int getDefaults() {
		int defaults = DEFAULT_LIGHTS;
		if(settings.getBoolean("notifySound", true))
			defaults |= DEFAULT_SOUND;
		if(settings.getBoolean("notifyVibration", true))
			defaults |= Notification.DEFAULT_VIBRATE;
		return defaults;
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
		} else if(!settings.getBoolean("notifyGroupPosts", true)) {
			return;
		} else {
			NotificationCompat.Builder b =
					new NotificationCompat.Builder(appContext);
			b.setSmallIcon(R.drawable.message_notification_icon);
			b.setContentTitle(appContext.getText(R.string.app_name));
			b.setContentText(appContext.getResources().getQuantityString(
					R.plurals.group_post_notification_text, groupTotal,
					groupTotal));
			b.setDefaults(getDefaults());
			b.setOnlyAlertOnce(true);
			if(groupCounts.size() == 1) {
				Intent i = new Intent(appContext, GroupActivity.class);
				GroupId g = groupCounts.keySet().iterator().next();
				i.putExtra("briar.GROUP_ID", g.getBytes());
				i.setFlags(FLAG_ACTIVITY_NEW_TASK | FLAG_ACTIVITY_SINGLE_TOP);
				TaskStackBuilder t = TaskStackBuilder.create(appContext);
				t.addParentStack(GroupActivity.class);
				t.addNextIntent(i);
				b.setContentIntent(t.getPendingIntent(0, FLAG_UPDATE_CURRENT));
			} else {
				Intent i = new Intent(appContext, GroupListActivity.class);
				i.setFlags(FLAG_ACTIVITY_NEW_TASK | FLAG_ACTIVITY_SINGLE_TOP);
				TaskStackBuilder t = TaskStackBuilder.create(appContext);
				t.addParentStack(GroupListActivity.class);
				t.addNextIntent(i);
				b.setContentIntent(t.getPendingIntent(0, FLAG_UPDATE_CURRENT));
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
