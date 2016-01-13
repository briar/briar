package org.briarproject.android;

import android.app.Application;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.TaskStackBuilder;

import org.briarproject.R;
import org.briarproject.android.contact.ContactListActivity;
import org.briarproject.android.contact.ConversationActivity;
import org.briarproject.android.forum.ForumActivity;
import org.briarproject.android.forum.ForumListActivity;
import org.briarproject.api.Settings;
import org.briarproject.api.android.AndroidNotificationManager;
import org.briarproject.api.contact.ContactId;
import org.briarproject.api.db.DatabaseComponent;
import org.briarproject.api.db.DatabaseExecutor;
import org.briarproject.api.db.DbException;
import org.briarproject.api.event.Event;
import org.briarproject.api.event.EventBus;
import org.briarproject.api.event.EventListener;
import org.briarproject.api.event.SettingsUpdatedEvent;
import org.briarproject.api.sync.GroupId;
import org.briarproject.util.StringUtils;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Logger;

import javax.inject.Inject;

import static android.app.Notification.DEFAULT_LIGHTS;
import static android.app.Notification.DEFAULT_SOUND;
import static android.app.Notification.DEFAULT_VIBRATE;
import static android.content.Context.NOTIFICATION_SERVICE;
import static android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP;
import static android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP;
import static java.util.logging.Level.WARNING;

class AndroidNotificationManagerImpl implements AndroidNotificationManager,
EventListener {

	private static final int PRIVATE_MESSAGE_NOTIFICATION_ID = 3;
	private static final int FORUM_POST_NOTIFICATION_ID = 4;
	private static final String CONTACT_URI =
			"content://org.briarproject/contact";
	private static final String FORUM_URI =
			"content://org.briarproject/forum";

	private static final Logger LOG =
			Logger.getLogger(AndroidNotificationManagerImpl.class.getName());

	private final DatabaseComponent db;
	private final Executor dbExecutor;
	private final EventBus eventBus;
	private final Context appContext;
	private final Lock lock = new ReentrantLock();

	// The following are locking: lock
	private final Map<ContactId, Integer> contactCounts =
			new HashMap<ContactId, Integer>();
	private final Map<GroupId, Integer> forumCounts =
			new HashMap<GroupId, Integer>();
	private int contactTotal = 0, forumTotal = 0;
	private int nextRequestId = 0;
	private ContactId activeContact;

	private volatile Settings settings = new Settings();

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
				} catch (DbException e) {
					if (LOG.isLoggable(WARNING))
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
		if (e instanceof SettingsUpdatedEvent) loadSettings();
	}

	public void showPrivateMessageNotification(ContactId c) {
		lock.lock();
		try {
			// check first if user has this conversation open at the moment
			if (activeContact == null || !activeContact.equals(c)) {
				Integer count = contactCounts.get(c);
				if (count == null) contactCounts.put(c, 1);
				else contactCounts.put(c, count + 1);
				contactTotal++;
				updatePrivateMessageNotification();
			}
		} finally {
			lock.unlock();
		}
	}

	public void clearPrivateMessageNotification(ContactId c) {
		lock.lock();
		try {
			Integer count = contactCounts.remove(c);
			if (count == null) return; // Already cleared
			contactTotal -= count;
			updatePrivateMessageNotification();
		} finally {
			lock.unlock();
		}
	}

	public void blockPrivateMessageNotification(ContactId c) {
		lock.lock();
		try {
			activeContact = c;
		} finally {
			lock.unlock();
		}
	}

	public void unblockPrivateMessageNotification(ContactId c) {
		lock.lock();
		try {
			if (activeContact != null && activeContact.equals(c)) {
				activeContact = null;
			}
		} finally {
			lock.unlock();
		}
	}

	// Locking: lock
	private void updatePrivateMessageNotification() {
		if (contactTotal == 0) {
			clearPrivateMessageNotification();
		} else if (settings.getBoolean("notifyPrivateMessages", true)) {
			NotificationCompat.Builder b =
					new NotificationCompat.Builder(appContext);
			b.setSmallIcon(R.drawable.message_notification_icon);
			b.setContentTitle(appContext.getText(R.string.app_name));
			b.setContentText(appContext.getResources().getQuantityString(
					R.plurals.private_message_notification_text, contactTotal,
					contactTotal));
			boolean sound = settings.getBoolean("notifySound", true);
			String ringtoneUri = settings.get("notifyRingtoneUri");
			if (sound && !StringUtils.isNullOrEmpty(ringtoneUri))
				b.setSound(Uri.parse(ringtoneUri));
			b.setDefaults(getDefaults());
			b.setOnlyAlertOnce(true);
			b.setAutoCancel(true);
			if (contactCounts.size() == 1) {
				Intent i = new Intent(appContext, ConversationActivity.class);
				ContactId c = contactCounts.keySet().iterator().next();
				i.putExtra("briar.CONTACT_ID", c.getInt());
				i.setData(Uri.parse(CONTACT_URI + "/" + c.getInt()));
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

	// Locking: lock
	private void clearPrivateMessageNotification() {
		Object o = appContext.getSystemService(NOTIFICATION_SERVICE);
		NotificationManager nm = (NotificationManager) o;
		nm.cancel(PRIVATE_MESSAGE_NOTIFICATION_ID);
	}

	private int getDefaults() {
		int defaults = DEFAULT_LIGHTS;
		boolean sound = settings.getBoolean("notifySound", true);
		String ringtoneUri = settings.get("notifyRingtoneUri");
		if (sound && StringUtils.isNullOrEmpty(ringtoneUri))
			defaults |= DEFAULT_SOUND;
		if (settings.getBoolean("notifyVibration", true))
			defaults |= DEFAULT_VIBRATE;
		return defaults;
	}

	public void showForumPostNotification(GroupId g) {
		lock.lock();
		try {
			Integer count = forumCounts.get(g);
			if (count == null) forumCounts.put(g, 1);
			else forumCounts.put(g, count + 1);
			forumTotal++;
			updateForumPostNotification();
		} finally {
			lock.unlock();
		}
	}

	public void clearForumPostNotification(GroupId g) {
		lock.lock();
		try {
			Integer count = forumCounts.remove(g);
			if (count == null) return; // Already cleared
			forumTotal -= count;
			updateForumPostNotification();
		} finally {
			lock.unlock();
		}
	}

	// Locking: lock
	private void updateForumPostNotification() {
		if (forumTotal == 0) {
			clearForumPostNotification();
		} else if (settings.getBoolean("notifyForumPosts", true)) {
			NotificationCompat.Builder b =
					new NotificationCompat.Builder(appContext);
			b.setSmallIcon(R.drawable.message_notification_icon);
			b.setContentTitle(appContext.getText(R.string.app_name));
			b.setContentText(appContext.getResources().getQuantityString(
					R.plurals.forum_post_notification_text, forumTotal,
					forumTotal));
			String ringtoneUri = settings.get("notifyRingtoneUri");
			if (!StringUtils.isNullOrEmpty(ringtoneUri))
				b.setSound(Uri.parse(ringtoneUri));
			b.setDefaults(getDefaults());
			b.setOnlyAlertOnce(true);
			b.setAutoCancel(true);
			if (forumCounts.size() == 1) {
				Intent i = new Intent(appContext, ForumActivity.class);
				GroupId g = forumCounts.keySet().iterator().next();
				i.putExtra("briar.GROUP_ID", g.getBytes());
				String idHex = StringUtils.toHexString(g.getBytes());
				i.setData(Uri.parse(FORUM_URI + "/" + idHex));
				i.setFlags(FLAG_ACTIVITY_CLEAR_TOP | FLAG_ACTIVITY_SINGLE_TOP);
				TaskStackBuilder t = TaskStackBuilder.create(appContext);
				t.addParentStack(ForumActivity.class);
				t.addNextIntent(i);
				b.setContentIntent(t.getPendingIntent(nextRequestId++, 0));
			} else {
				Intent i = new Intent(appContext, ForumListActivity.class);
				i.setFlags(FLAG_ACTIVITY_CLEAR_TOP | FLAG_ACTIVITY_SINGLE_TOP);
				TaskStackBuilder t = TaskStackBuilder.create(appContext);
				t.addParentStack(ForumListActivity.class);
				t.addNextIntent(i);
				b.setContentIntent(t.getPendingIntent(nextRequestId++, 0));
			}
			Object o = appContext.getSystemService(NOTIFICATION_SERVICE);
			NotificationManager nm = (NotificationManager) o;
			nm.notify(FORUM_POST_NOTIFICATION_ID, b.build());
		}
	}

	// Locking: lock
	private void clearForumPostNotification() {
		Object o = appContext.getSystemService(NOTIFICATION_SERVICE);
		NotificationManager nm = (NotificationManager) o;
		nm.cancel(FORUM_POST_NOTIFICATION_ID);
	}

	public void clearNotifications() {
		lock.lock();
		try {
			contactCounts.clear();
			forumCounts.clear();
			contactTotal = forumTotal = 0;
			clearPrivateMessageNotification();
			clearForumPostNotification();
		} finally {
			lock.unlock();
		}
	}
}
