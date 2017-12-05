package org.briarproject.briar.android;

import android.annotation.TargetApi;
import android.app.Application;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.support.annotation.StringRes;
import android.support.annotation.UiThread;
import android.support.v4.app.TaskStackBuilder;

import org.briarproject.bramble.api.contact.ContactId;
import org.briarproject.bramble.api.db.DatabaseExecutor;
import org.briarproject.bramble.api.db.DbException;
import org.briarproject.bramble.api.event.Event;
import org.briarproject.bramble.api.event.EventListener;
import org.briarproject.bramble.api.lifecycle.Service;
import org.briarproject.bramble.api.lifecycle.ServiceException;
import org.briarproject.bramble.api.nullsafety.MethodsNotNullByDefault;
import org.briarproject.bramble.api.nullsafety.ParametersNotNullByDefault;
import org.briarproject.bramble.api.settings.Settings;
import org.briarproject.bramble.api.settings.SettingsManager;
import org.briarproject.bramble.api.settings.event.SettingsUpdatedEvent;
import org.briarproject.bramble.api.sync.GroupId;
import org.briarproject.bramble.api.system.AndroidExecutor;
import org.briarproject.bramble.api.system.Clock;
import org.briarproject.bramble.util.StringUtils;
import org.briarproject.briar.R;
import org.briarproject.briar.android.contact.ConversationActivity;
import org.briarproject.briar.android.forum.ForumActivity;
import org.briarproject.briar.android.navdrawer.NavDrawerActivity;
import org.briarproject.briar.android.privategroup.conversation.GroupActivity;
import org.briarproject.briar.android.util.BriarNotificationBuilder;
import org.briarproject.briar.api.android.AndroidNotificationManager;
import org.briarproject.briar.api.blog.event.BlogPostAddedEvent;
import org.briarproject.briar.api.forum.event.ForumPostReceivedEvent;
import org.briarproject.briar.api.introduction.event.IntroductionRequestReceivedEvent;
import org.briarproject.briar.api.introduction.event.IntroductionResponseReceivedEvent;
import org.briarproject.briar.api.introduction.event.IntroductionSucceededEvent;
import org.briarproject.briar.api.messaging.event.PrivateMessageReceivedEvent;
import org.briarproject.briar.api.privategroup.event.GroupMessageAddedEvent;
import org.briarproject.briar.api.sharing.event.InvitationRequestReceivedEvent;
import org.briarproject.briar.api.sharing.event.InvitationResponseReceivedEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;

import javax.annotation.concurrent.ThreadSafe;
import javax.inject.Inject;

import static android.app.Notification.DEFAULT_LIGHTS;
import static android.app.Notification.DEFAULT_SOUND;
import static android.app.Notification.DEFAULT_VIBRATE;
import static android.app.NotificationManager.IMPORTANCE_DEFAULT;
import static android.content.Context.NOTIFICATION_SERVICE;
import static android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP;
import static android.os.Build.VERSION.SDK_INT;
import static android.support.v4.app.NotificationCompat.CATEGORY_MESSAGE;
import static android.support.v4.app.NotificationCompat.CATEGORY_SOCIAL;
import static java.util.logging.Level.WARNING;
import static org.briarproject.briar.android.activity.BriarActivity.GROUP_ID;
import static org.briarproject.briar.android.contact.ConversationActivity.CONTACT_ID;
import static org.briarproject.briar.android.navdrawer.NavDrawerActivity.INTENT_BLOGS;
import static org.briarproject.briar.android.navdrawer.NavDrawerActivity.INTENT_CONTACTS;
import static org.briarproject.briar.android.navdrawer.NavDrawerActivity.INTENT_FORUMS;
import static org.briarproject.briar.android.navdrawer.NavDrawerActivity.INTENT_GROUPS;
import static org.briarproject.briar.android.settings.SettingsFragment.SETTINGS_NAMESPACE;

@ThreadSafe
@MethodsNotNullByDefault
@ParametersNotNullByDefault
class AndroidNotificationManagerImpl implements AndroidNotificationManager,
		Service, EventListener {

	// Notification IDs
	private static final int PRIVATE_MESSAGE_NOTIFICATION_ID = 3;
	private static final int GROUP_MESSAGE_NOTIFICATION_ID = 4;
	private static final int FORUM_POST_NOTIFICATION_ID = 5;
	private static final int BLOG_POST_NOTIFICATION_ID = 6;
	private static final int INTRODUCTION_SUCCESS_NOTIFICATION_ID = 7;

	// Channel IDs
	private static final String CONTACT_CHANNEL_ID = "contacts";
	private static final String GROUP_CHANNEL_ID = "groups";
	private static final String FORUM_CHANNEL_ID = "forums";
	private static final String BLOG_CHANNEL_ID = "blogs";

	private static final long SOUND_DELAY = TimeUnit.SECONDS.toMillis(2);

	private static final Logger LOG =
			Logger.getLogger(AndroidNotificationManagerImpl.class.getName());

	private final Executor dbExecutor;
	private final SettingsManager settingsManager;
	private final AndroidExecutor androidExecutor;
	private final Clock clock;
	private final Context appContext;
	private final NotificationManager notificationManager;
	private final AtomicBoolean used = new AtomicBoolean(false);

	// The following must only be accessed on the main UI thread
	private final Map<ContactId, Integer> contactCounts = new HashMap<>();
	private final Map<GroupId, Integer> groupCounts = new HashMap<>();
	private final Map<GroupId, Integer> forumCounts = new HashMap<>();
	private final Map<GroupId, Integer> blogCounts = new HashMap<>();
	private int contactTotal = 0, groupTotal = 0, forumTotal = 0, blogTotal = 0;
	private int introductionTotal = 0;
	private int nextRequestId = 0;
	private ContactId blockedContact = null;
	private GroupId blockedGroup = null;
	private boolean blockContacts = false, blockGroups = false;
	private boolean blockForums = false, blockBlogs = false;
	private boolean blockIntroductions = false;
	private long lastSound = 0;

	private volatile Settings settings = new Settings();

	@Inject
	AndroidNotificationManagerImpl(@DatabaseExecutor Executor dbExecutor,
			SettingsManager settingsManager, AndroidExecutor androidExecutor,
			Application app, Clock clock) {
		this.dbExecutor = dbExecutor;
		this.settingsManager = settingsManager;
		this.androidExecutor = androidExecutor;
		this.clock = clock;
		appContext = app.getApplicationContext();
		notificationManager = (NotificationManager)
				appContext.getSystemService(NOTIFICATION_SERVICE);
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
		if (SDK_INT >= 26) {
			// Create notification channels
			Callable<Void> task = () -> {
				createNotificationChannel(CONTACT_CHANNEL_ID,
						R.string.contact_list_button);
				createNotificationChannel(GROUP_CHANNEL_ID,
						R.string.groups_button);
				createNotificationChannel(FORUM_CHANNEL_ID,
						R.string.forums_button);
				createNotificationChannel(BLOG_CHANNEL_ID,
						R.string.blogs_button);
				return null;
			};
			try {
				androidExecutor.runOnUiThread(task).get();
			} catch (InterruptedException | ExecutionException e) {
				throw new ServiceException(e);
			}
		}
	}

	@TargetApi(26)
	private void createNotificationChannel(String channelId,
			@StringRes int name) {
		notificationManager.createNotificationChannel(
				new NotificationChannel(channelId, appContext.getString(name),
						IMPORTANCE_DEFAULT));
	}

	@Override
	public void stopService() throws ServiceException {
		// Clear all notifications
		Future<Void> f = androidExecutor.runOnUiThread(() -> {
			clearContactNotification();
			clearGroupMessageNotification();
			clearForumPostNotification();
			clearBlogPostNotification();
			clearIntroductionSuccessNotification();
			return null;
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
		notificationManager.cancel(PRIVATE_MESSAGE_NOTIFICATION_ID);
	}

	@UiThread
	private void clearGroupMessageNotification() {
		groupCounts.clear();
		groupTotal = 0;
		notificationManager.cancel(GROUP_MESSAGE_NOTIFICATION_ID);
	}

	@UiThread
	private void clearForumPostNotification() {
		forumCounts.clear();
		forumTotal = 0;
		notificationManager.cancel(FORUM_POST_NOTIFICATION_ID);
	}

	@UiThread
	private void clearBlogPostNotification() {
		blogCounts.clear();
		blogTotal = 0;
		notificationManager.cancel(BLOG_POST_NOTIFICATION_ID);
	}

	@UiThread
	private void clearIntroductionSuccessNotification() {
		introductionTotal = 0;
		notificationManager.cancel(INTRODUCTION_SUCCESS_NOTIFICATION_ID);
	}

	@Override
	public void eventOccurred(Event e) {
		if (e instanceof SettingsUpdatedEvent) {
			SettingsUpdatedEvent s = (SettingsUpdatedEvent) e;
			if (s.getNamespace().equals(SETTINGS_NAMESPACE)) loadSettings();
		} else if (e instanceof PrivateMessageReceivedEvent) {
			PrivateMessageReceivedEvent p = (PrivateMessageReceivedEvent) e;
			showContactNotification(p.getContactId());
		} else if (e instanceof GroupMessageAddedEvent) {
			GroupMessageAddedEvent g = (GroupMessageAddedEvent) e;
			if (!g.isLocal()) showGroupMessageNotification(g.getGroupId());
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
		dbExecutor.execute(() -> {
			try {
				settings = settingsManager.getSettings(SETTINGS_NAMESPACE);
			} catch (DbException e) {
				if (LOG.isLoggable(WARNING))
					LOG.log(WARNING, e.toString(), e);
			}
		});
	}

	private void showContactNotification(ContactId c) {
		androidExecutor.runOnUiThread(() -> {
			if (blockContacts) return;
			if (c.equals(blockedContact)) return;
			Integer count = contactCounts.get(c);
			if (count == null) contactCounts.put(c, 1);
			else contactCounts.put(c, count + 1);
			contactTotal++;
			updateContactNotification(true);
		});
	}

	@Override
	public void clearContactNotification(ContactId c) {
		androidExecutor.runOnUiThread(() -> {
			Integer count = contactCounts.remove(c);
			if (count == null) return; // Already cleared
			contactTotal -= count;
			updateContactNotification(false);
		});
	}

	@UiThread
	private void updateContactNotification(boolean mayAlertAgain) {
		if (contactTotal == 0) {
			clearContactNotification();
		} else if (settings.getBoolean(PREF_NOTIFY_PRIVATE, true)) {
			BriarNotificationBuilder b = new BriarNotificationBuilder(
					appContext, CONTACT_CHANNEL_ID);
			b.setSmallIcon(R.drawable.notification_private_message);
			b.setColorRes(R.color.briar_primary);
			b.setContentTitle(appContext.getText(R.string.app_name));
			b.setContentText(appContext.getResources().getQuantityString(
					R.plurals.private_message_notification_text, contactTotal,
					contactTotal));
			b.setNumber(contactTotal);
			boolean showOnLockScreen =
					settings.getBoolean(PREF_NOTIFY_LOCK_SCREEN, false);
			b.setLockscreenVisibility(CATEGORY_MESSAGE, showOnLockScreen);
			if (mayAlertAgain) setAlertProperties(b);
			setDeleteIntent(b, CONTACT_URI);
			if (contactCounts.size() == 1) {
				// Touching the notification shows the relevant conversation
				Intent i = new Intent(appContext, ConversationActivity.class);
				ContactId c = contactCounts.keySet().iterator().next();
				i.putExtra(CONTACT_ID, c.getInt());
				i.setData(Uri.parse(CONTACT_URI + "/" + c.getInt()));
				i.setFlags(FLAG_ACTIVITY_CLEAR_TOP);
				TaskStackBuilder t = TaskStackBuilder.create(appContext);
				t.addParentStack(ConversationActivity.class);
				t.addNextIntent(i);
				b.setContentIntent(t.getPendingIntent(nextRequestId++, 0));
			} else {
				// Touching the notification shows the contact list
				Intent i = new Intent(appContext, NavDrawerActivity.class);
				i.putExtra(INTENT_CONTACTS, true);
				i.setFlags(FLAG_ACTIVITY_CLEAR_TOP);
				i.setData(Uri.parse(CONTACT_URI));
				TaskStackBuilder t = TaskStackBuilder.create(appContext);
				t.addParentStack(NavDrawerActivity.class);
				t.addNextIntent(i);
				b.setContentIntent(t.getPendingIntent(nextRequestId++, 0));
			}
			notificationManager.notify(PRIVATE_MESSAGE_NOTIFICATION_ID,
					b.build());
		}
	}

	@UiThread
	private void setAlertProperties(BriarNotificationBuilder b) {
		long currentTime = clock.currentTimeMillis();
		if (currentTime - lastSound > SOUND_DELAY) {
			boolean sound = settings.getBoolean(PREF_NOTIFY_SOUND, true);
			String ringtoneUri = settings.get(PREF_NOTIFY_RINGTONE_URI);
			if (sound && !StringUtils.isNullOrEmpty(ringtoneUri))
				b.setSound(Uri.parse(ringtoneUri));
			b.setDefaults(getDefaults());
			lastSound = currentTime;
		}
	}

	@UiThread
	private int getDefaults() {
		int defaults = DEFAULT_LIGHTS;
		boolean sound = settings.getBoolean(PREF_NOTIFY_SOUND, true);
		String ringtoneUri = settings.get(PREF_NOTIFY_RINGTONE_URI);
		if (sound && StringUtils.isNullOrEmpty(ringtoneUri))
			defaults |= DEFAULT_SOUND;
		if (settings.getBoolean(PREF_NOTIFY_VIBRATION, true))
			defaults |= DEFAULT_VIBRATE;
		return defaults;
	}

	private void setDeleteIntent(BriarNotificationBuilder b, String uri) {
		Intent i = new Intent(appContext, NotificationCleanupService.class);
		i.setData(Uri.parse(uri));
		b.setDeleteIntent(PendingIntent.getService(appContext, nextRequestId++,
				i, 0));
	}

	@Override
	public void clearAllContactNotifications() {
		androidExecutor.runOnUiThread(
				(Runnable) this::clearContactNotification);
	}

	@UiThread
	private void showGroupMessageNotification(GroupId g) {
		androidExecutor.runOnUiThread(() -> {
			if (blockGroups) return;
			if (g.equals(blockedGroup)) return;
			Integer count = groupCounts.get(g);
			if (count == null) groupCounts.put(g, 1);
			else groupCounts.put(g, count + 1);
			groupTotal++;
			updateGroupMessageNotification(true);
		});
	}

	@Override
	public void clearGroupMessageNotification(GroupId g) {
		androidExecutor.runOnUiThread(() -> {
			Integer count = groupCounts.remove(g);
			if (count == null) return; // Already cleared
			groupTotal -= count;
			updateGroupMessageNotification(false);
		});
	}

	@UiThread
	private void updateGroupMessageNotification(boolean mayAlertAgain) {
		if (groupTotal == 0) {
			clearGroupMessageNotification();
		} else if (settings.getBoolean(PREF_NOTIFY_GROUP, true)) {
			BriarNotificationBuilder b =
					new BriarNotificationBuilder(appContext, GROUP_CHANNEL_ID);
			b.setSmallIcon(R.drawable.notification_private_group);
			b.setColorRes(R.color.briar_primary);
			b.setContentTitle(appContext.getText(R.string.app_name));
			b.setContentText(appContext.getResources().getQuantityString(
					R.plurals.group_message_notification_text, groupTotal,
					groupTotal));
			b.setNumber(groupTotal);
			boolean showOnLockScreen =
					settings.getBoolean(PREF_NOTIFY_LOCK_SCREEN, false);
			b.setLockscreenVisibility(CATEGORY_SOCIAL, showOnLockScreen);
			if (mayAlertAgain) setAlertProperties(b);
			setDeleteIntent(b, GROUP_URI);
			if (groupCounts.size() == 1) {
				// Touching the notification shows the relevant group
				Intent i = new Intent(appContext, GroupActivity.class);
				GroupId g = groupCounts.keySet().iterator().next();
				i.putExtra(GROUP_ID, g.getBytes());
				String idHex = StringUtils.toHexString(g.getBytes());
				i.setData(Uri.parse(GROUP_URI + "/" + idHex));
				i.setFlags(FLAG_ACTIVITY_CLEAR_TOP);
				TaskStackBuilder t = TaskStackBuilder.create(appContext);
				t.addParentStack(GroupActivity.class);
				t.addNextIntent(i);
				b.setContentIntent(t.getPendingIntent(nextRequestId++, 0));
			} else {
				// Touching the notification shows the group list
				Intent i = new Intent(appContext, NavDrawerActivity.class);
				i.putExtra(INTENT_GROUPS, true);
				i.setFlags(FLAG_ACTIVITY_CLEAR_TOP);
				i.setData(Uri.parse(GROUP_URI));
				TaskStackBuilder t = TaskStackBuilder.create(appContext);
				t.addParentStack(NavDrawerActivity.class);
				t.addNextIntent(i);
				b.setContentIntent(t.getPendingIntent(nextRequestId++, 0));
			}
			notificationManager.notify(GROUP_MESSAGE_NOTIFICATION_ID,
					b.build());
		}
	}

	@Override
	public void clearAllGroupMessageNotifications() {
		androidExecutor.runOnUiThread(
				(Runnable) this::clearGroupMessageNotification);
	}

	@UiThread
	private void showForumPostNotification(GroupId g) {
		androidExecutor.runOnUiThread(() -> {
			if (blockForums) return;
			if (g.equals(blockedGroup)) return;
			Integer count = forumCounts.get(g);
			if (count == null) forumCounts.put(g, 1);
			else forumCounts.put(g, count + 1);
			forumTotal++;
			updateForumPostNotification(true);
		});
	}

	@Override
	public void clearForumPostNotification(GroupId g) {
		androidExecutor.runOnUiThread(() -> {
			Integer count = forumCounts.remove(g);
			if (count == null) return; // Already cleared
			forumTotal -= count;
			updateForumPostNotification(false);
		});
	}

	@UiThread
	private void updateForumPostNotification(boolean mayAlertAgain) {
		if (forumTotal == 0) {
			clearForumPostNotification();
		} else if (settings.getBoolean(PREF_NOTIFY_FORUM, true)) {
			BriarNotificationBuilder b =
					new BriarNotificationBuilder(appContext, FORUM_CHANNEL_ID);
			b.setSmallIcon(R.drawable.notification_forum);
			b.setColorRes(R.color.briar_primary);
			b.setContentTitle(appContext.getText(R.string.app_name));
			b.setContentText(appContext.getResources().getQuantityString(
					R.plurals.forum_post_notification_text, forumTotal,
					forumTotal));
			b.setNumber(forumTotal);
			boolean showOnLockScreen =
					settings.getBoolean(PREF_NOTIFY_LOCK_SCREEN, false);
			b.setLockscreenVisibility(CATEGORY_SOCIAL, showOnLockScreen);
			if (mayAlertAgain) setAlertProperties(b);
			setDeleteIntent(b, FORUM_URI);
			if (forumCounts.size() == 1) {
				// Touching the notification shows the relevant forum
				Intent i = new Intent(appContext, ForumActivity.class);
				GroupId g = forumCounts.keySet().iterator().next();
				i.putExtra(GROUP_ID, g.getBytes());
				String idHex = StringUtils.toHexString(g.getBytes());
				i.setData(Uri.parse(FORUM_URI + "/" + idHex));
				i.setFlags(FLAG_ACTIVITY_CLEAR_TOP);
				TaskStackBuilder t = TaskStackBuilder.create(appContext);
				t.addParentStack(ForumActivity.class);
				t.addNextIntent(i);
				b.setContentIntent(t.getPendingIntent(nextRequestId++, 0));
			} else {
				// Touching the notification shows the forum list
				Intent i = new Intent(appContext, NavDrawerActivity.class);
				i.putExtra(INTENT_FORUMS, true);
				i.setFlags(FLAG_ACTIVITY_CLEAR_TOP);
				i.setData(Uri.parse(FORUM_URI));
				TaskStackBuilder t = TaskStackBuilder.create(appContext);
				t.addParentStack(NavDrawerActivity.class);
				t.addNextIntent(i);
				b.setContentIntent(t.getPendingIntent(nextRequestId++, 0));
			}
			notificationManager.notify(FORUM_POST_NOTIFICATION_ID, b.build());
		}
	}

	@Override
	public void clearAllForumPostNotifications() {
		androidExecutor.runOnUiThread(
				(Runnable) this::clearForumPostNotification);
	}

	@UiThread
	private void showBlogPostNotification(GroupId g) {
		androidExecutor.runOnUiThread(() -> {
			if (blockBlogs) return;
			if (g.equals(blockedGroup)) return;
			Integer count = blogCounts.get(g);
			if (count == null) blogCounts.put(g, 1);
			else blogCounts.put(g, count + 1);
			blogTotal++;
			updateBlogPostNotification(true);
		});
	}

	@Override
	public void clearBlogPostNotification(GroupId g) {
		androidExecutor.runOnUiThread(() -> {
			Integer count = blogCounts.remove(g);
			if (count == null) return; // Already cleared
			blogTotal -= count;
			updateBlogPostNotification(false);
		});
	}

	@UiThread
	private void updateBlogPostNotification(boolean mayAlertAgain) {
		if (blogTotal == 0) {
			clearBlogPostNotification();
		} else if (settings.getBoolean(PREF_NOTIFY_BLOG, true)) {
			BriarNotificationBuilder b =
					new BriarNotificationBuilder(appContext, BLOG_CHANNEL_ID);
			b.setSmallIcon(R.drawable.notification_blog);
			b.setColorRes(R.color.briar_primary);
			b.setContentTitle(appContext.getText(R.string.app_name));
			b.setContentText(appContext.getResources().getQuantityString(
					R.plurals.blog_post_notification_text, blogTotal,
					blogTotal));
			b.setNumber(blogTotal);
			boolean showOnLockScreen =
					settings.getBoolean(PREF_NOTIFY_LOCK_SCREEN, false);
			b.setLockscreenVisibility(CATEGORY_SOCIAL, showOnLockScreen);
			if (mayAlertAgain) setAlertProperties(b);
			setDeleteIntent(b, BLOG_URI);
			// Touching the notification shows the combined blog feed
			Intent i = new Intent(appContext, NavDrawerActivity.class);
			i.putExtra(INTENT_BLOGS, true);
			i.setFlags(FLAG_ACTIVITY_CLEAR_TOP);
			i.setData(Uri.parse(BLOG_URI));
			TaskStackBuilder t = TaskStackBuilder.create(appContext);
			t.addParentStack(NavDrawerActivity.class);
			t.addNextIntent(i);
			b.setContentIntent(t.getPendingIntent(nextRequestId++, 0));

			notificationManager.notify(BLOG_POST_NOTIFICATION_ID, b.build());
		}
	}

	@Override
	public void clearAllBlogPostNotifications() {
		androidExecutor.runOnUiThread(
				(Runnable) this::clearBlogPostNotification);
	}

	private void showIntroductionNotification() {
		androidExecutor.runOnUiThread(() -> {
			if (blockIntroductions) return;
			introductionTotal++;
			updateIntroductionNotification();
		});
	}

	@UiThread
	private void updateIntroductionNotification() {
		BriarNotificationBuilder b =
				new BriarNotificationBuilder(appContext, CONTACT_CHANNEL_ID);
		b.setSmallIcon(R.drawable.notification_introduction);
		b.setColorRes(R.color.briar_primary);
		b.setContentTitle(appContext.getText(R.string.app_name));
		b.setContentText(appContext.getResources().getQuantityString(
				R.plurals.introduction_notification_text, introductionTotal,
				introductionTotal));
		boolean showOnLockScreen =
				settings.getBoolean(PREF_NOTIFY_LOCK_SCREEN, false);
		b.setLockscreenVisibility(CATEGORY_MESSAGE, showOnLockScreen);
		setAlertProperties(b);
		setDeleteIntent(b, INTRODUCTION_URI);
		// Touching the notification shows the contact list
		Intent i = new Intent(appContext, NavDrawerActivity.class);
		i.putExtra(INTENT_CONTACTS, true);
		i.setFlags(FLAG_ACTIVITY_CLEAR_TOP);
		i.setData(Uri.parse(CONTACT_URI));
		TaskStackBuilder t = TaskStackBuilder.create(appContext);
		t.addParentStack(NavDrawerActivity.class);
		t.addNextIntent(i);
		b.setContentIntent(t.getPendingIntent(nextRequestId++, 0));

		notificationManager.notify(INTRODUCTION_SUCCESS_NOTIFICATION_ID,
				b.build());
	}

	@Override
	public void clearAllIntroductionNotifications() {
		androidExecutor.runOnUiThread(
				this::clearIntroductionSuccessNotification);
	}

	@Override
	public void blockNotification(GroupId g) {
		androidExecutor.runOnUiThread((Runnable) () -> blockedGroup = g);
	}

	@Override
	public void unblockNotification(GroupId g) {
		androidExecutor.runOnUiThread(() -> {
			if (g.equals(blockedGroup)) blockedGroup = null;
		});
	}

	@Override
	public void blockContactNotification(ContactId c) {
		androidExecutor.runOnUiThread((Runnable) () -> blockedContact = c);
	}

	@Override
	public void unblockContactNotification(ContactId c) {
		androidExecutor.runOnUiThread(() -> {
			if (c.equals(blockedContact)) blockedContact = null;
		});
	}

	@Override
	public void blockAllBlogPostNotifications() {
		androidExecutor.runOnUiThread((Runnable) () -> blockBlogs = true);
	}

	@Override
	public void unblockAllBlogPostNotifications() {
		androidExecutor.runOnUiThread((Runnable) () -> blockBlogs = false);
	}
}
