package org.briarproject.android;

import static android.app.Notification.DEFAULT_LIGHTS;
import static android.app.Notification.DEFAULT_SOUND;
import static android.app.Notification.DEFAULT_VIBRATE;
import static android.content.Context.NOTIFICATION_SERVICE;
import static android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP;
import static android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP;
import static java.util.logging.Level.WARNING;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
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
import org.briarproject.api.db.DatabaseComponent;
import org.briarproject.api.db.DatabaseExecutor;
import org.briarproject.api.db.DbException;
import org.briarproject.api.event.Event;
import org.briarproject.api.event.EventBus;
import org.briarproject.api.event.EventListener;
import org.briarproject.api.event.SettingsUpdatedEvent;
import org.briarproject.api.lifecycle.Service;
import org.briarproject.api.messaging.GroupId;
import org.briarproject.util.StringUtils;

import android.app.Application;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.TaskStackBuilder;

class AndroidNotificationManagerImpl implements AndroidNotificationManager,
Service, EventListener {

	private static final int PRIVATE_MESSAGE_NOTIFICATION_ID = 3;
	private static final int GROUP_POST_NOTIFICATION_ID = 4;

	private static final Logger LOG =
			Logger.getLogger(AndroidNotificationManagerImpl.class.getName());

	private final DatabaseComponent db;
	private final Executor dbExecutor;
	private final EventBus eventBus;
	private final Context appContext;
	private final Map<ContactId, Integer> contactCounts =
			new HashMap<ContactId, Integer>(); 
	private final Map<GroupId, Integer> groupCounts =
			new HashMap<GroupId, Integer>(); 

	private int privateTotal = 0, groupTotal = 0;
	private int nextRequestId = 0;

	private volatile Settings settings = new Settings();
	
	private final Lock synchLock = new ReentrantLock();

	@Inject
	public AndroidNotificationManagerImpl(DatabaseComponent db,
			@DatabaseExecutor Executor dbExecutor, EventBus eventBus,
			Application app) {
		this.db = db;
		this.dbExecutor = dbExecutor;
		this.eventBus = eventBus;
		appContext = app.getApplicationContext();
	}

	public boolean start() {
		eventBus.addListener(this);
		loadSettings();
		return true;
	}

	private void loadSettings() {
		dbExecutor.execute(new Runnable() {
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
		eventBus.removeListener(this);
		return true;
	}

	public void eventOccurred(Event e) {
		if(e instanceof SettingsUpdatedEvent) loadSettings();
	}

	public void showPrivateMessageNotification(ContactId c) {
		synchLock.lock();
		try{
			Integer count = contactCounts.get(c);
			if(count == null) contactCounts.put(c, 1);
			else contactCounts.put(c, count + 1);
			privateTotal++;
			updatePrivateMessageNotification();
		}
		finally{
			synchLock.unlock();
		}
	}

	public void clearPrivateMessageNotification(ContactId c) {
		synchLock.lock();
		try{
			Integer count = contactCounts.remove(c);
			if(count == null) return; // Already cleared
			privateTotal -= count;
			updatePrivateMessageNotification();
		}
		finally{
			synchLock.unlock();
		}
	}

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
			boolean sound = settings.getBoolean("notifySound", true);
			String ringtoneUri = settings.get("notifyRingtoneUri");
			if(sound && !StringUtils.isNullOrEmpty(ringtoneUri))
				b.setSound(Uri.parse(ringtoneUri));
			b.setDefaults(getDefaults());
			b.setOnlyAlertOnce(true);
			b.setAutoCancel(true);
			if(contactCounts.size() == 1) {
				Intent i = new Intent(appContext, ConversationActivity.class);
				ContactId c = contactCounts.keySet().iterator().next();
				i.putExtra("briar.CONTACT_ID", c.getInt());
				i.setFlags(FLAG_ACTIVITY_CLEAR_TOP | FLAG_ACTIVITY_SINGLE_TOP);
				TaskStackBuilder t = TaskStackBuilder.create(appContext);
				t.addParentStack(ConversationActivity.class);
				t.addNextIntent(i);
				b.setContentIntent(t.getPendingIntent(nextRequestId++, 0));
			} else {
				Intent i = new Intent(appContext, ContactListActivity.class);
				i.setFlags(FLAG_ACTIVITY_CLEAR_TOP | FLAG_ACTIVITY_SINGLE_TOP);
				TaskStackBuilder t = TaskStackBuilder.create(appContext);
				t.addParentStack(ContactListActivity.class);
				t.addNextIntent(i);
				b.setContentIntent(t.getPendingIntent(nextRequestId++, 0));
			}
			Object o = appContext.getSystemService(NOTIFICATION_SERVICE);
			NotificationManager nm = (NotificationManager) o;
			nm.notify(PRIVATE_MESSAGE_NOTIFICATION_ID, b.build());
		}
	}

	private void clearPrivateMessageNotification() {
		Object o = appContext.getSystemService(NOTIFICATION_SERVICE);
		NotificationManager nm = (NotificationManager) o;
		nm.cancel(PRIVATE_MESSAGE_NOTIFICATION_ID);
	}

	private int getDefaults() {
		int defaults = DEFAULT_LIGHTS;
		boolean sound = settings.getBoolean("notifySound", true);
		String ringtoneUri = settings.get("notifyRingtoneUri");
		if(sound && StringUtils.isNullOrEmpty(ringtoneUri))
			defaults |= DEFAULT_SOUND;
		if(settings.getBoolean("notifyVibration", true))
			defaults |= DEFAULT_VIBRATE;
		return defaults;
	}

	public void showGroupPostNotification(GroupId g) {
		synchLock.lock();
		try{
			Integer count = groupCounts.get(g);
			if(count == null) groupCounts.put(g, 1);
			else groupCounts.put(g, count + 1);
			groupTotal++;
			updateGroupPostNotification();
		}
		finally{
			synchLock.unlock();
		}
	}

	public void clearGroupPostNotification(GroupId g) {
		synchLock.lock();
		try{
		Integer count = groupCounts.remove(g);
		if(count == null) return; // Already cleared
		groupTotal -= count;
		updateGroupPostNotification();
		}
		finally{
			synchLock.unlock();
		}
	}

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
			String ringtoneUri = settings.get("notifyRingtoneUri");
			if(!StringUtils.isNullOrEmpty(ringtoneUri))
				b.setSound(Uri.parse(ringtoneUri));
			b.setDefaults(getDefaults());
			b.setOnlyAlertOnce(true);
			b.setAutoCancel(true);
			if(groupCounts.size() == 1) {
				Intent i = new Intent(appContext, GroupActivity.class);
				GroupId g = groupCounts.keySet().iterator().next();
				i.putExtra("briar.GROUP_ID", g.getBytes());
				i.setFlags(FLAG_ACTIVITY_CLEAR_TOP | FLAG_ACTIVITY_SINGLE_TOP);
				TaskStackBuilder t = TaskStackBuilder.create(appContext);
				t.addParentStack(GroupActivity.class);
				t.addNextIntent(i);
				b.setContentIntent(t.getPendingIntent(nextRequestId++, 0));
			} else {
				Intent i = new Intent(appContext, GroupListActivity.class);
				i.setFlags(FLAG_ACTIVITY_CLEAR_TOP | FLAG_ACTIVITY_SINGLE_TOP);
				TaskStackBuilder t = TaskStackBuilder.create(appContext);
				t.addParentStack(GroupListActivity.class);
				t.addNextIntent(i);
				b.setContentIntent(t.getPendingIntent(nextRequestId++, 0));
			}
			Object o = appContext.getSystemService(NOTIFICATION_SERVICE);
			NotificationManager nm = (NotificationManager) o;
			nm.notify(GROUP_POST_NOTIFICATION_ID, b.build());
		}
	}

	private void clearGroupPostNotification() {
		Object o = appContext.getSystemService(NOTIFICATION_SERVICE);
		NotificationManager nm = (NotificationManager) o;
		nm.cancel(GROUP_POST_NOTIFICATION_ID);
	}

	public void clearNotifications() {
		synchLock.lock();
		try{
			contactCounts.clear();
			groupCounts.clear();
			privateTotal = groupTotal = 0;
			clearPrivateMessageNotification();
			clearGroupPostNotification();
		}
		finally{
			synchLock.unlock();
		}
	}
}
