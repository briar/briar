package org.briarproject.android;

import android.app.Application;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.Uri;
import android.os.Build;
import android.support.annotation.UiThread;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.TaskStackBuilder;

import org.briarproject.R;
import org.briarproject.android.api.AndroidExecutor;
import org.briarproject.android.api.AndroidNotificationManager;
import org.briarproject.android.contact.ConversationActivity;
import org.briarproject.android.forum.ForumActivity;
import org.briarproject.api.contact.ContactId;
import org.briarproject.api.db.DatabaseExecutor;
import org.briarproject.api.db.DbException;
import org.briarproject.api.event.BlogPostAddedEvent;
import org.briarproject.api.event.Event;
import org.briarproject.api.event.EventListener;
import org.briarproject.api.event.ForumPostReceivedEvent;
import org.briarproject.api.event.IntroductionRequestReceivedEvent;
import org.briarproject.api.event.IntroductionResponseReceivedEvent;
import org.briarproject.api.event.IntroductionSucceededEvent;
import org.briarproject.api.event.InvitationRequestReceivedEvent;
import org.briarproject.api.event.InvitationResponseReceivedEvent;
import org.briarproject.api.event.PrivateMessageReceivedEvent;
import org.briarproject.api.event.SettingsUpdatedEvent;
import org.briarproject.api.lifecycle.Service;
import org.briarproject.api.lifecycle.ServiceException;
import org.briarproject.api.settings.Settings;
import org.briarproject.api.settings.SettingsManager;
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
import static org.briarproject.android.NavDrawerActivity.INTENT_BLOGS;
import static org.briarproject.android.NavDrawerActivity.INTENT_CONTACTS;
import static org.briarproject.android.NavDrawerActivity.INTENT_FORUMS;
import static org.briarproject.android.contact.ConversationActivity.CONTACT_ID;
import static org.briarproject.android.fragment.SettingsFragment.PREF_NOTIFY_BLOG;
import static org.briarproject.android.fragment.SettingsFragment.SETTINGS_NAMESPACE;

class AndroidNotificationManagerImpl implements AndroidNotificationManager,
		Service, EventListener {

	// Notification IDs
	private static final int PRIVATE_MESSAGE_NOTIFICATION_ID = 3;
	private static final int FORUM_POST_NOTIFICATION_ID = 4;
	private static final int BLOG_POST_NOTIFICATION_ID = 5;
	private static final int INTRODUCTION_SUCCESS_NOTIFICATION_ID = 6;

	// Content URIs to differentiate between pending intents
	private static final String CONTACT_URI =
			"content://org.briarproject/contact";
	private static final String FORUM_URI =
			"content://org.briarproject/forum";
	private static final String BLOG_URI =
			"content://org.briarproject/blog";

	// Actions for intents that are broadcast when notifications are dismissed
	private static final String CLEAR_PRIVATE_MESSAGE_ACTION =
			"org.briarproject.briar.CLEAR_PRIVATE_MESSAGE_NOTIFICATION";
	private static final String CLEAR_FORUM_ACTION =
			"org.briarproject.briar.CLEAR_FORUM_NOTIFICATION";
	private static final String CLEAR_BLOG_ACTION =
			"org.briarproject.briar.CLEAR_BLOG_NOTIFICATION";
	private static final String CLEAR_INTRODUCTION_ACTION =
			"org.briarproject.briar.CLEAR_INTRODUCTION_NOTIFICATION";

	private static final Logger LOG =
			Logger.getLogger(AndroidNotificationManagerImpl.class.getName());

	private final Executor dbExecutor;
	private final SettingsManager settingsManager;
	private final AndroidExecutor androidExecutor;
	private final Context appContext;
	private final BroadcastReceiver receiver = new DeleteIntentReceiver();
	private final AtomicBoolean used = new AtomicBoolean(false);

	// The following must only be accessed on the main UI thread
	private final Map<ContactId, Integer> contactCounts = new HashMap<>();
	private final Map<GroupId, Integer> forumCounts = new HashMap<>();
	private final Map<GroupId, Integer> blogCounts = new HashMap<>();
	private int contactTotal = 0, forumTotal = 0, blogTotal = 0;
	private int introductionTotal = 0;
	private int nextRequestId = 0;
	private ContactId blockedContact = null;
	private GroupId blockedGroup = null;
	private boolean blockContacts = false, blockForums = false;
	private boolean blockBlogs = false, blockIntroductions = false;

	private volatile Settings settings = new Settings();

	@Inject
	AndroidNotificationManagerImpl(@DatabaseExecutor Executor dbExecutor,
			SettingsManager settingsManager, AndroidExecutor androidExecutor,
			Application app) {
		this.dbExecutor = dbExecutor;
		this.settingsManager = settingsManager;
		this.androidExecutor = androidExecutor;
		appContext = app.getApplicationContext();
	}

	@Override
	public void startService() throws ServiceException {
		if (used.getAndSet(true)) throw new IllegalStateException();
		// Load settings
		try {
			settings = settingsManager.getSettings(SETTINGS_NAMESPACE);
		} catch (DbException e) {
			throw new ServiceException(e);
		}
		// Register a broadcast receiver for notifications being dismissed
		Future<Void> f = androidExecutor.runOnUiThread(new Callable<Void>() {
			@Override
			public Void call() {
				IntentFilter filter = new IntentFilter();
				filter.addAction(CLEAR_PRIVATE_MESSAGE_ACTION);
				filter.addAction(CLEAR_FORUM_ACTION);
				filter.addAction(CLEAR_BLOG_ACTION);
				filter.addAction(CLEAR_INTRODUCTION_ACTION);
				appContext.registerReceiver(receiver, filter);
				return null;
			}
		});
		try {
			f.get();
		} catch (InterruptedException | ExecutionException e) {
			throw new ServiceException(e);
		}
	}

	@Override
	public void stopService() throws ServiceException {
		// Clear all notifications and unregister the broadcast receiver
		Future<Void> f = androidExecutor.runOnUiThread(new Callable<Void>() {
			@Override
			public Void call() {
				clearContactNotification();
				clearForumPostNotification();
				clearBlogPostNotification();
				clearIntroductionSuccessNotification();
				appContext.unregisterReceiver(receiver);
				return null;
			}
		});
		try {
			f.get();
		} catch (InterruptedException | ExecutionException e) {
			throw new ServiceException(e);
		}
	}

	@UiThread
	private void clearContactNotification() {
		contactCounts.clear();
		contactTotal = 0;
		Object o = appContext.getSystemService(NOTIFICATION_SERVICE);
		NotificationManager nm = (NotificationManager) o;
		nm.cancel(PRIVATE_MESSAGE_NOTIFICATION_ID);
	}

	@UiThread
	private void clearForumPostNotification() {
		forumCounts.clear();
		forumTotal = 0;
		Object o = appContext.getSystemService(NOTIFICATION_SERVICE);
		NotificationManager nm = (NotificationManager) o;
		nm.cancel(FORUM_POST_NOTIFICATION_ID);
	}

	@UiThread
	private void clearBlogPostNotification() {
		blogCounts.clear();
		blogTotal = 0;
		Object o = appContext.getSystemService(NOTIFICATION_SERVICE);
		NotificationManager nm = (NotificationManager) o;
		nm.cancel(BLOG_POST_NOTIFICATION_ID);
	}

	@UiThread
	private void clearIntroductionSuccessNotification() {
		introductionTotal = 0;
		Object o = appContext.getSystemService(NOTIFICATION_SERVICE);
		NotificationManager nm = (NotificationManager) o;
		nm.cancel(INTRODUCTION_SUCCESS_NOTIFICATION_ID);
	}

	@Override
	public void eventOccurred(Event e) {
		if (e instanceof SettingsUpdatedEvent) {
			SettingsUpdatedEvent s = (SettingsUpdatedEvent) e;
			if (s.getNamespace().equals(SETTINGS_NAMESPACE)) loadSettings();
		} else if (e instanceof PrivateMessageReceivedEvent) {
			PrivateMessageReceivedEvent p = (PrivateMessageReceivedEvent) e;
			showContactNotification(p.getContactId());
		} else if (e instanceof ForumPostReceivedEvent) {
			ForumPostReceivedEvent f = (ForumPostReceivedEvent) e;
			showForumPostNotification(f.getGroupId());
		} else if (e instanceof BlogPostAddedEvent) {
			BlogPostAddedEvent b = (BlogPostAddedEvent) e;
			showBlogPostNotification(b.getGroupId());
		} else if (e instanceof IntroductionRequestReceivedEvent) {
			ContactId c = ((IntroductionRequestReceivedEvent) e).getContactId();
			showContactNotification(c);
		} else if (e instanceof IntroductionResponseReceivedEvent) {
			ContactId c =
					((IntroductionResponseReceivedEvent) e).getContactId();
			showContactNotification(c);
		} else if (e instanceof InvitationRequestReceivedEvent) {
			ContactId c = ((InvitationRequestReceivedEvent) e).getContactId();
			showContactNotification(c);
		} else if (e instanceof InvitationResponseReceivedEvent) {
			ContactId c = ((InvitationResponseReceivedEvent) e).getContactId();
			showContactNotification(c);
		} else if (e instanceof IntroductionSucceededEvent) {
			showIntroductionNotification();
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

	private void showContactNotification(final ContactId c) {
		androidExecutor.runOnUiThread(new Runnable() {
			@Override
			public void run() {
				if (blockContacts) return;
				if (c.equals(blockedContact)) return;
				Integer count = contactCounts.get(c);
				if (count == null) contactCounts.put(c, 1);
				else contactCounts.put(c, count + 1);
				contactTotal++;
				updateContactNotification();
			}
		});
	}

	@Override
	public void clearContactNotification(final ContactId c) {
		androidExecutor.runOnUiThread(new Runnable() {
			@Override
			public void run() {
				Integer count = contactCounts.remove(c);
				if (count == null) return; // Already cleared
				contactTotal -= count;
				updateContactNotification();
			}
		});
	}

	@UiThread
	private void updateContactNotification() {
		if (contactTotal == 0) {
			clearContactNotification();
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
			// Clear the counters if the notification is dismissed
			Intent clear = new Intent(CLEAR_PRIVATE_MESSAGE_ACTION);
			PendingIntent delete = PendingIntent.getBroadcast(appContext, 0,
					clear, 0);
			b.setDeleteIntent(delete);
			if (contactCounts.size() == 1) {
				// Touching the notification shows the relevant conversation
				Intent i = new Intent(appContext, ConversationActivity.class);
				ContactId c = contactCounts.keySet().iterator().next();
				i.putExtra(CONTACT_ID, c.getInt());
				i.setData(Uri.parse(CONTACT_URI + "/" + c.getInt()));
				i.setFlags(FLAG_ACTIVITY_CLEAR_TOP | FLAG_ACTIVITY_SINGLE_TOP);
				TaskStackBuilder t = TaskStackBuilder.create(appContext);
				t.addParentStack(ConversationActivity.class);
				t.addNextIntent(i);
				b.setContentIntent(t.getPendingIntent(nextRequestId++, 0));
			} else {
				// Touching the notification shows the contact list
				Intent i = new Intent(appContext, NavDrawerActivity.class);
				i.putExtra(INTENT_CONTACTS, true);
				i.setFlags(FLAG_ACTIVITY_CLEAR_TOP | FLAG_ACTIVITY_SINGLE_TOP);
				i.setData(Uri.parse(CONTACT_URI));
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

	@UiThread
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
	public void clearAllContactNotifications() {
		androidExecutor.runOnUiThread(new Runnable() {
			@Override
			public void run() {
				clearContactNotification();
				clearIntroductionSuccessNotification();
			}
		});
	}

	@UiThread
	private void showForumPostNotification(final GroupId g) {
		androidExecutor.runOnUiThread(new Runnable() {
			@Override
			public void run() {
				if (blockForums) return;
				if (g.equals(blockedGroup)) return;
				Integer count = forumCounts.get(g);
				if (count == null) forumCounts.put(g, 1);
				else forumCounts.put(g, count + 1);
				forumTotal++;
				updateForumPostNotification();
			}
		});
	}

	@Override
	public void clearForumPostNotification(final GroupId g) {
		androidExecutor.runOnUiThread(new Runnable() {
			@Override
			public void run() {
				Integer count = forumCounts.remove(g);
				if (count == null) return; // Already cleared
				forumTotal -= count;
				updateForumPostNotification();
			}
		});
	}

	@UiThread
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
			// Clear the counters if the notification is dismissed
			Intent clear = new Intent(CLEAR_FORUM_ACTION);
			PendingIntent delete = PendingIntent.getBroadcast(appContext, 0,
					clear, 0);
			b.setDeleteIntent(delete);
			if (forumCounts.size() == 1) {
				// Touching the notification shows the relevant forum
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
				// Touching the notification shows the forum list
				Intent i = new Intent(appContext, NavDrawerActivity.class);
				i.putExtra(INTENT_FORUMS, true);
				i.setFlags(FLAG_ACTIVITY_CLEAR_TOP | FLAG_ACTIVITY_SINGLE_TOP);
				i.setData(Uri.parse(FORUM_URI));
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
	public void clearAllForumPostNotifications() {
		androidExecutor.runOnUiThread(new Runnable() {
			@Override
			public void run() {
				clearForumPostNotification();
			}
		});
	}

	@UiThread
	private void showBlogPostNotification(final GroupId g) {
		androidExecutor.runOnUiThread(new Runnable() {
			@Override
			public void run() {
				if (blockBlogs) return;
				if (g.equals(blockedGroup)) return;
				Integer count = blogCounts.get(g);
				if (count == null) blogCounts.put(g, 1);
				else blogCounts.put(g, count + 1);
				blogTotal++;
				updateBlogPostNotification();
			}
		});
	}

	@Override
	public void clearBlogPostNotification(final GroupId g) {
		androidExecutor.runOnUiThread(new Runnable() {
			@Override
			public void run() {
				Integer count = blogCounts.remove(g);
				if (count == null) return; // Already cleared
				blogTotal -= count;
				updateBlogPostNotification();
			}
		});
	}

	@UiThread
	private void updateBlogPostNotification() {
		if (blogTotal == 0) {
			clearBlogPostNotification();
		} else if (settings.getBoolean(PREF_NOTIFY_BLOG, true)) {
			NotificationCompat.Builder b =
					new NotificationCompat.Builder(appContext);
			b.setSmallIcon(R.drawable.message_notification_icon);
			b.setContentTitle(appContext.getText(R.string.app_name));
			b.setContentText(appContext.getResources().getQuantityString(
					R.plurals.blog_post_notification_text, blogTotal,
					blogTotal));
			String ringtoneUri = settings.get("notifyRingtoneUri");
			if (!StringUtils.isNullOrEmpty(ringtoneUri))
				b.setSound(Uri.parse(ringtoneUri));
			b.setDefaults(getDefaults());
			b.setOnlyAlertOnce(true);
			b.setAutoCancel(true);
			// Clear the counters if the notification is dismissed
			Intent clear = new Intent(CLEAR_BLOG_ACTION);
			PendingIntent delete = PendingIntent.getBroadcast(appContext, 0,
					clear, 0);
			b.setDeleteIntent(delete);
			// Touching the notification shows the combined blog feed
			Intent i = new Intent(appContext, NavDrawerActivity.class);
			i.putExtra(INTENT_BLOGS, true);
			i.setFlags(FLAG_ACTIVITY_CLEAR_TOP | FLAG_ACTIVITY_SINGLE_TOP);
			i.setData(Uri.parse(BLOG_URI));
			TaskStackBuilder t = TaskStackBuilder.create(appContext);
			t.addParentStack(NavDrawerActivity.class);
			t.addNextIntent(i);
			b.setContentIntent(t.getPendingIntent(nextRequestId++, 0));
			if (Build.VERSION.SDK_INT >= 21) {
				b.setCategory(CATEGORY_SOCIAL);
				b.setVisibility(VISIBILITY_SECRET);
			}
			Object o = appContext.getSystemService(NOTIFICATION_SERVICE);
			NotificationManager nm = (NotificationManager) o;
			nm.notify(BLOG_POST_NOTIFICATION_ID, b.build());
		}
	}

	@Override
	public void clearAllBlogPostNotifications() {
		androidExecutor.runOnUiThread(new Runnable() {
			@Override
			public void run() {
				clearBlogPostNotification();
			}
		});
	}

	private void showIntroductionNotification() {
		androidExecutor.runOnUiThread(new Runnable() {
			@Override
			public void run() {
				if (blockIntroductions) return;
				introductionTotal++;
				updateIntroductionNotification();
			}
		});
	}

	@UiThread
	private void updateIntroductionNotification() {
		NotificationCompat.Builder b =
				new NotificationCompat.Builder(appContext);
		b.setSmallIcon(R.drawable.introduction_notification);
		b.setContentTitle(appContext.getText(R.string.app_name));
		b.setContentText(appContext.getResources().getQuantityString(
				R.plurals.introduction_notification_text, introductionTotal,
				introductionTotal));
		String ringtoneUri = settings.get("notifyRingtoneUri");
		if (!StringUtils.isNullOrEmpty(ringtoneUri))
			b.setSound(Uri.parse(ringtoneUri));
		b.setDefaults(getDefaults());
		b.setOnlyAlertOnce(true);
		b.setAutoCancel(true);
		// Clear the counter if the notification is dismissed
		Intent clear = new Intent(CLEAR_INTRODUCTION_ACTION);
		PendingIntent delete = PendingIntent.getBroadcast(appContext, 0,
				clear, 0);
		b.setDeleteIntent(delete);
		// Touching the notification shows the contact list
		Intent i = new Intent(appContext, NavDrawerActivity.class);
		i.putExtra(INTENT_CONTACTS, true);
		i.setFlags(FLAG_ACTIVITY_CLEAR_TOP | FLAG_ACTIVITY_SINGLE_TOP);
		i.setData(Uri.parse(CONTACT_URI));
		TaskStackBuilder t = TaskStackBuilder.create(appContext);
		t.addParentStack(NavDrawerActivity.class);
		t.addNextIntent(i);
		b.setContentIntent(t.getPendingIntent(nextRequestId++, 0));
		if (Build.VERSION.SDK_INT >= 21) {
			b.setCategory(CATEGORY_MESSAGE);
			b.setVisibility(VISIBILITY_SECRET);
		}
		Object o = appContext.getSystemService(NOTIFICATION_SERVICE);
		NotificationManager nm = (NotificationManager) o;
		nm.notify(INTRODUCTION_SUCCESS_NOTIFICATION_ID, b.build());
	}

	@Override
	public void blockNotification(final GroupId g) {
		androidExecutor.runOnUiThread(new Runnable() {
			@Override
			public void run() {
				blockedGroup = g;
			}
		});
	}

	@Override
	public void unblockNotification(final GroupId g) {
		androidExecutor.runOnUiThread(new Runnable() {
			@Override
			public void run() {
				if (g.equals(blockedGroup)) blockedGroup = null;
			}
		});
	}

	@Override
	public void blockContactNotification(final ContactId c) {
		androidExecutor.runOnUiThread(new Runnable() {
			@Override
			public void run() {
				blockedContact = c;
			}
		});
	}

	@Override
	public void unblockContactNotification(final ContactId c) {
		androidExecutor.runOnUiThread(new Runnable() {
			@Override
			public void run() {
				if (c.equals(blockedContact)) blockedContact = null;
			}
		});
	}

	@Override
	public void blockAllContactNotifications() {
		androidExecutor.runOnUiThread(new Runnable() {
			@Override
			public void run() {
				blockContacts = true;
				blockIntroductions = true;
			}
		});
	}

	@Override
	public void unblockAllContactNotifications() {
		androidExecutor.runOnUiThread(new Runnable() {
			@Override
			public void run() {
				blockContacts = false;
				blockIntroductions = false;
			}
		});
	}

	@Override
	public void blockAllForumPostNotifications() {
		androidExecutor.runOnUiThread(new Runnable() {
			@Override
			public void run() {
				blockForums = true;
			}
		});
	}

	@Override
	public void unblockAllForumPostNotifications() {
		androidExecutor.runOnUiThread(new Runnable() {
			@Override
			public void run() {
				blockForums = false;
			}
		});
	}

	@Override
	public void blockAllBlogPostNotifications() {
		androidExecutor.runOnUiThread(new Runnable() {
			@Override
			public void run() {
				blockBlogs = true;
			}
		});
	}

	@Override
	public void unblockAllBlogPostNotifications() {
		androidExecutor.runOnUiThread(new Runnable() {
			@Override
			public void run() {
				blockBlogs = false;
			}
		});
	}

	private class DeleteIntentReceiver extends BroadcastReceiver {

		@Override
		public void onReceive(Context context, Intent intent) {
			final String action = intent.getAction();
			androidExecutor.runOnUiThread(new Runnable() {
				@Override
				public void run() {
					if (CLEAR_PRIVATE_MESSAGE_ACTION.equals(action)) {
						clearContactNotification();
					} else if (CLEAR_FORUM_ACTION.equals(action)) {
						clearForumPostNotification();
					} else if (CLEAR_BLOG_ACTION.equals(action)) {
						clearBlogPostNotification();
					} else if (CLEAR_INTRODUCTION_ACTION.equals(action)) {
						clearIntroductionSuccessNotification();
					}
				}
			});
		}
	}
}
