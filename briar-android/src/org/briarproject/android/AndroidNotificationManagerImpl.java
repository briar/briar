package org.briarproject.android;

import android.app.Application;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.TaskStackBuilder;

import org.briarproject.R;
import org.briarproject.android.api.AndroidExecutor;
import org.briarproject.android.api.AndroidNotificationManager;
import org.briarproject.android.contact.ConversationActivity;
import org.briarproject.android.forum.ForumActivity;
import org.briarproject.api.contact.Contact;
import org.briarproject.api.contact.ContactId;
import org.briarproject.api.db.DatabaseExecutor;
import org.briarproject.api.db.DbException;
import org.briarproject.api.event.Event;
import org.briarproject.api.event.EventListener;
import org.briarproject.api.event.ForumInvitationReceivedEvent;
import org.briarproject.api.event.IntroductionRequestReceivedEvent;
import org.briarproject.api.event.IntroductionResponseReceivedEvent;
import org.briarproject.api.event.IntroductionSucceededEvent;
import org.briarproject.api.event.MessageValidatedEvent;
import org.briarproject.api.event.SettingsUpdatedEvent;
import org.briarproject.api.forum.ForumManager;
import org.briarproject.api.lifecycle.Service;
import org.briarproject.api.lifecycle.ServiceException;
import org.briarproject.api.messaging.MessagingManager;
import org.briarproject.api.settings.Settings;
import org.briarproject.api.settings.SettingsManager;
import org.briarproject.api.sync.ClientId;
import org.briarproject.api.sync.GroupId;
import org.briarproject.util.StringUtils;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;

import javax.inject.Inject;

import static android.app.Notification.DEFAULT_LIGHTS;
import static android.app.Notification.DEFAULT_SOUND;
import static android.app.Notification.DEFAULT_VIBRATE;
import static android.content.Context.NOTIFICATION_SERVICE;
import static android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP;
import static android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP;
import static android.support.v4.app.NotificationCompat.CATEGORY_MESSAGE;
import static android.support.v4.app.NotificationCompat.CATEGORY_SOCIAL;
import static android.support.v4.app.NotificationCompat.VISIBILITY_SECRET;
import static java.util.logging.Level.WARNING;
import static org.briarproject.android.BriarActivity.GROUP_ID;
import static org.briarproject.android.fragment.SettingsFragment.SETTINGS_NAMESPACE;

class AndroidNotificationManagerImpl implements AndroidNotificationManager,
		Service, EventListener {

	private static final int PRIVATE_MESSAGE_NOTIFICATION_ID = 3;
	private static final int FORUM_POST_NOTIFICATION_ID = 4;
	private static final int INTRODUCTION_SUCCESS_NOTIFICATION_ID = 5;
	private static final String CONTACT_URI =
			"content://org.briarproject/contact";
	private static final String FORUM_URI =
			"content://org.briarproject/forum";

	private static final Logger LOG =
			Logger.getLogger(AndroidNotificationManagerImpl.class.getName());

	private final Executor dbExecutor;
	private final SettingsManager settingsManager;
	private final MessagingManager messagingManager;
	private final ForumManager forumManager;
	private final AndroidExecutor androidExecutor;
	private final Context appContext;

	// The following must only be accessed on the main UI thread
	private final Map<GroupId, Integer> contactCounts = new HashMap<>();
	private final Map<GroupId, Integer> forumCounts = new HashMap<>();
	private final AtomicBoolean used = new AtomicBoolean(false);

	private int contactTotal = 0, forumTotal = 0;
	private int nextRequestId = 0;
	private GroupId visibleGroup = null;

	private volatile Settings settings = new Settings();

	@Inject
	public AndroidNotificationManagerImpl(@DatabaseExecutor Executor dbExecutor,
			SettingsManager settingsManager, MessagingManager messagingManager,
			ForumManager forumManager, AndroidExecutor androidExecutor,
			Application app) {
		this.dbExecutor = dbExecutor;
		this.settingsManager = settingsManager;
		this.messagingManager = messagingManager;
		this.forumManager = forumManager;
		this.androidExecutor = androidExecutor;
		appContext = app.getApplicationContext();
	}

	@Override
	public void startService() throws ServiceException {
		if (used.getAndSet(true)) throw new IllegalStateException();
		try {
			settings = settingsManager.getSettings(SETTINGS_NAMESPACE);
		} catch (DbException e) {
			throw new ServiceException(e);
		}
	}

	@Override
	public void stopService() throws ServiceException {
		Future<Void> f = androidExecutor.submit(new Callable<Void>() {
			@Override
			public Void call() {
				clearPrivateMessageNotification();
				clearForumPostNotification();
				clearIntroductionSuccessNotification();
				return null;
			}
		});
		try {
			f.get();
		} catch (InterruptedException | ExecutionException e) {
			throw new ServiceException(e);
		}
	}

	private void clearPrivateMessageNotification() {
		Object o = appContext.getSystemService(NOTIFICATION_SERVICE);
		NotificationManager nm = (NotificationManager) o;
		nm.cancel(PRIVATE_MESSAGE_NOTIFICATION_ID);
	}

	private void clearForumPostNotification() {
		Object o = appContext.getSystemService(NOTIFICATION_SERVICE);
		NotificationManager nm = (NotificationManager) o;
		nm.cancel(FORUM_POST_NOTIFICATION_ID);
	}

	private void clearIntroductionSuccessNotification() {
		Object o = appContext.getSystemService(NOTIFICATION_SERVICE);
		NotificationManager nm = (NotificationManager) o;
		nm.cancel(INTRODUCTION_SUCCESS_NOTIFICATION_ID);
	}

	@Override
	public void eventOccurred(Event e) {
		if (e instanceof SettingsUpdatedEvent) {
			SettingsUpdatedEvent s = (SettingsUpdatedEvent) e;
			if (s.getNamespace().equals(SETTINGS_NAMESPACE)) loadSettings();
		} else if (e instanceof MessageValidatedEvent) {
			MessageValidatedEvent m = (MessageValidatedEvent) e;
			if (m.isValid() && !m.isLocal()) {
				ClientId c = m.getClientId();
				if (c.equals(messagingManager.getClientId()))
					showPrivateMessageNotification(m.getMessage().getGroupId());
				else if (c.equals(forumManager.getClientId()))
					showForumPostNotification(m.getMessage().getGroupId());
			}
		} else if (e instanceof IntroductionRequestReceivedEvent) {
			ContactId c = ((IntroductionRequestReceivedEvent) e).getContactId();
			showNotificationForPrivateConversation(c);
		} else if (e instanceof IntroductionResponseReceivedEvent) {
			ContactId c = ((IntroductionResponseReceivedEvent) e).getContactId();
			showNotificationForPrivateConversation(c);
		} else if (e instanceof IntroductionSucceededEvent) {
			Contact c = ((IntroductionSucceededEvent) e).getContact();
			showIntroductionSucceededNotification(c);
		} else if (e instanceof ForumInvitationReceivedEvent) {
			ContactId c = ((ForumInvitationReceivedEvent) e).getContactId();
			showNotificationForPrivateConversation(c);
		}
	}

	private void loadSettings() {
		dbExecutor.execute(new Runnable() {
			@Override
			public void run() {
				try {
					settings = settingsManager.getSettings(SETTINGS_NAMESPACE);
				} catch (DbException e) {
					if (LOG.isLoggable(WARNING))
						LOG.log(WARNING, e.toString(), e);
				}
			}
		});
	}

	@Override
	public void showPrivateMessageNotification(final GroupId g) {
		androidExecutor.execute(new Runnable() {
			@Override
			public void run() {
				Integer count = contactCounts.get(g);
				if (count == null) contactCounts.put(g, 1);
				else contactCounts.put(g, count + 1);
				contactTotal++;
				if (!g.equals(visibleGroup))
					updatePrivateMessageNotification();
			}
		});
	}

	@Override
	public void clearPrivateMessageNotification(final GroupId g) {
		androidExecutor.execute(new Runnable() {
			@Override
			public void run() {
				Integer count = contactCounts.remove(g);
				if (count == null) return; // Already cleared
				contactTotal -= count;
				// FIXME: If the notification isn't showing, this may show it
				updatePrivateMessageNotification();
			}
		});
	}

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
				GroupId g = contactCounts.keySet().iterator().next();
				i.putExtra(GROUP_ID, g.getBytes());
				String idHex = StringUtils.toHexString(g.getBytes());
				i.setData(Uri.parse(CONTACT_URI + "/" + idHex));
				i.setFlags(FLAG_ACTIVITY_CLEAR_TOP | FLAG_ACTIVITY_SINGLE_TOP);
				TaskStackBuilder t = TaskStackBuilder.create(appContext);
				t.addParentStack(ConversationActivity.class);
				t.addNextIntent(i);
				b.setContentIntent(t.getPendingIntent(nextRequestId++, 0));
			} else {
				Intent i = new Intent(appContext, NavDrawerActivity.class);
				i.putExtra(NavDrawerActivity.INTENT_CONTACTS, true);
				i.setFlags(FLAG_ACTIVITY_CLEAR_TOP | FLAG_ACTIVITY_SINGLE_TOP);
				TaskStackBuilder t = TaskStackBuilder.create(appContext);
				t.addParentStack(NavDrawerActivity.class);
				t.addNextIntent(i);
				b.setContentIntent(t.getPendingIntent(nextRequestId++, 0));
			}
			if (Build.VERSION.SDK_INT >= 21) {
				b.setCategory(CATEGORY_MESSAGE);
				b.setVisibility(VISIBILITY_SECRET);
			}
			Object o = appContext.getSystemService(NOTIFICATION_SERVICE);
			NotificationManager nm = (NotificationManager) o;
			nm.notify(PRIVATE_MESSAGE_NOTIFICATION_ID, b.build());
		}
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

	@Override
	public void showForumPostNotification(final GroupId g) {
		androidExecutor.execute(new Runnable() {
			@Override
			public void run() {
				Integer count = forumCounts.get(g);
				if (count == null) forumCounts.put(g, 1);
				else forumCounts.put(g, count + 1);
				forumTotal++;
				if (!g.equals(visibleGroup))
					updateForumPostNotification();
			}
		});
	}

	@Override
	public void clearForumPostNotification(final GroupId g) {
		androidExecutor.execute(new Runnable() {
			@Override
			public void run() {
				Integer count = forumCounts.remove(g);
				if (count == null) return; // Already cleared
				forumTotal -= count;
				// FIXME: If the notification isn't showing, this may show it
				updateForumPostNotification();
			}
		});
	}

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
				i.putExtra(GROUP_ID, g.getBytes());
				String idHex = StringUtils.toHexString(g.getBytes());
				i.setData(Uri.parse(FORUM_URI + "/" + idHex));
				i.setFlags(FLAG_ACTIVITY_CLEAR_TOP | FLAG_ACTIVITY_SINGLE_TOP);
				TaskStackBuilder t = TaskStackBuilder.create(appContext);
				t.addParentStack(ForumActivity.class);
				t.addNextIntent(i);
				b.setContentIntent(t.getPendingIntent(nextRequestId++, 0));
			} else {
				Intent i = new Intent(appContext, NavDrawerActivity.class);
				i.putExtra(NavDrawerActivity.INTENT_FORUMS, true);
				i.setFlags(FLAG_ACTIVITY_CLEAR_TOP | FLAG_ACTIVITY_SINGLE_TOP);
				TaskStackBuilder t = TaskStackBuilder.create(appContext);
				t.addParentStack(NavDrawerActivity.class);
				t.addNextIntent(i);
				b.setContentIntent(t.getPendingIntent(nextRequestId++, 0));
			}
			if (Build.VERSION.SDK_INT >= 21) {
				b.setCategory(CATEGORY_SOCIAL);
				b.setVisibility(VISIBILITY_SECRET);
			}
			Object o = appContext.getSystemService(NOTIFICATION_SERVICE);
			NotificationManager nm = (NotificationManager) o;
			nm.notify(FORUM_POST_NOTIFICATION_ID, b.build());
		}
	}

	@Override
	public void blockNotification(final GroupId g) {
		androidExecutor.execute(new Runnable() {
			@Override
			public void run() {
				visibleGroup = g;
			}
		});
	}

	@Override
	public void unblockNotification(final GroupId g) {
		androidExecutor.execute(new Runnable() {
			@Override
			public void run() {
				if (g.equals(visibleGroup)) visibleGroup = null;
			}
		});
	}

	private void showNotificationForPrivateConversation(final ContactId c) {
		androidExecutor.execute(new Runnable() {
			@Override
			public void run() {
				try {
					GroupId group = messagingManager.getConversationId(c);
					showPrivateMessageNotification(group);
				} catch (DbException e) {
					if (LOG.isLoggable(WARNING))
						LOG.log(WARNING, e.toString(), e);
				}
			}
		});
	}

	private void showIntroductionSucceededNotification(final Contact c) {
		androidExecutor.execute(new Runnable() {
			@Override
			public void run() {
				NotificationCompat.Builder b =
						new NotificationCompat.Builder(appContext);
				b.setSmallIcon(R.drawable.introduction_notification);

				b.setContentTitle(appContext
						.getString(R.string.introduction_success_title));
				b.setContentText(appContext
						.getString(R.string.introduction_success_text,
								c.getAuthor().getName()));
				b.setDefaults(getDefaults());
				b.setAutoCancel(true);

				Intent i = new Intent(appContext, NavDrawerActivity.class);
				i.putExtra(NavDrawerActivity.INTENT_CONTACTS, true);
				i.setFlags(FLAG_ACTIVITY_CLEAR_TOP | FLAG_ACTIVITY_SINGLE_TOP);
				TaskStackBuilder t = TaskStackBuilder.create(appContext);
				t.addParentStack(NavDrawerActivity.class);
				t.addNextIntent(i);
				b.setContentIntent(t.getPendingIntent(nextRequestId++, 0));

				Object o = appContext.getSystemService(NOTIFICATION_SERVICE);
				NotificationManager nm = (NotificationManager) o;
				nm.notify(INTRODUCTION_SUCCESS_NOTIFICATION_ID, b.build());
			}
		});
	}

}
