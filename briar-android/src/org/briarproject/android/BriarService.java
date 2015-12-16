package org.briarproject.android;

import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Binder;
import android.os.IBinder;
import android.support.v4.app.NotificationCompat;

import org.briarproject.R;
import org.briarproject.api.android.AndroidExecutor;
import org.briarproject.api.android.AndroidNotificationManager;
import org.briarproject.api.contact.ContactId;
import org.briarproject.api.db.DatabaseComponent;
import org.briarproject.api.db.DatabaseConfig;
import org.briarproject.api.db.DatabaseExecutor;
import org.briarproject.api.db.DbException;
import org.briarproject.api.event.Event;
import org.briarproject.api.event.EventBus;
import org.briarproject.api.event.EventListener;
import org.briarproject.api.event.MessageAddedEvent;
import org.briarproject.api.lifecycle.LifecycleManager;
import org.briarproject.api.lifecycle.LifecycleManager.StartResult;
import org.briarproject.api.sync.GroupId;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;

import javax.inject.Inject;

import roboguice.service.RoboService;

import static android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP;
import static android.content.Intent.FLAG_ACTIVITY_NEW_TASK;
import static android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP;
import static java.util.logging.Level.WARNING;
import static org.briarproject.api.lifecycle.LifecycleManager.StartResult.ALREADY_RUNNING;
import static org.briarproject.api.lifecycle.LifecycleManager.StartResult.SUCCESS;

public class BriarService extends RoboService implements EventListener {

	private static final int ONGOING_NOTIFICATION_ID = 1;
	private static final int FAILURE_NOTIFICATION_ID = 2;

	private static final Logger LOG =
			Logger.getLogger(BriarService.class.getName());

	private final AtomicBoolean created = new AtomicBoolean(false);
	private final Binder binder = new BriarBinder();

	@Inject private DatabaseConfig databaseConfig;
	@Inject private AndroidNotificationManager notificationManager;

	// Fields that are accessed from background threads must be volatile
	@Inject private volatile LifecycleManager lifecycleManager;
	@Inject private volatile AndroidExecutor androidExecutor;
	@Inject @DatabaseExecutor private volatile Executor dbExecutor;
	@Inject private volatile DatabaseComponent db;
	@Inject private volatile EventBus eventBus;
	private volatile boolean started = false;

	@Override
	public void onCreate() {
		super.onCreate();
		LOG.info("Created");
		if (created.getAndSet(true)) {
			LOG.info("Already created");
			stopSelf();
			return;
		}
		if (databaseConfig.getEncryptionKey() == null) {
			LOG.info("No database key");
			stopSelf();
			return;
		}
		// Show an ongoing notification that the service is running
		NotificationCompat.Builder b = new NotificationCompat.Builder(this);
		b.setSmallIcon(R.drawable.ongoing_notification_icon);
		b.setContentTitle(getText(R.string.ongoing_notification_title));
		b.setContentText(getText(R.string.ongoing_notification_text));
		b.setWhen(0); // Don't show the time
		b.setOngoing(true);
		Intent i = new Intent(this, DashboardActivity.class);
		i.setFlags(FLAG_ACTIVITY_NEW_TASK | FLAG_ACTIVITY_CLEAR_TOP |
				FLAG_ACTIVITY_SINGLE_TOP);
		b.setContentIntent(PendingIntent.getActivity(this, 0, i, 0));
		startForeground(ONGOING_NOTIFICATION_ID, b.build());
		// Start the services in a background thread
		new Thread() {
			@Override
			public void run() {
				StartResult result = lifecycleManager.startServices();
				if (result == SUCCESS) {
					eventBus.addListener(BriarService.this);
					started = true;
				} else if (result == ALREADY_RUNNING) {
					LOG.info("Already running");
					stopSelf();
				} else {
					if (LOG.isLoggable(WARNING))
						LOG.warning("Startup failed: " + result);
					showStartupFailureNotification(result);
					stopSelf();
				}
			}
		}.start();
	}

	private void showStartupFailureNotification(StartResult result) {
		NotificationCompat.Builder b = new NotificationCompat.Builder(this);
		b.setSmallIcon(android.R.drawable.stat_notify_error);
		b.setContentTitle(getText(R.string.startup_failed_notification_title));
		b.setContentText(getText(R.string.startup_failed_notification_text));
		Intent i = new Intent(this, StartupFailureActivity.class);
		i.setFlags(FLAG_ACTIVITY_NEW_TASK);
		i.putExtra("briar.START_RESULT", result);
		i.putExtra("briar.FAILURE_NOTIFICATION_ID", FAILURE_NOTIFICATION_ID);
		b.setContentIntent(PendingIntent.getActivity(this, 0, i, PendingIntent.FLAG_UPDATE_CURRENT));
		Object o = getSystemService(NOTIFICATION_SERVICE);
		NotificationManager nm = (NotificationManager) o;
		nm.notify(FAILURE_NOTIFICATION_ID, b.build());

		// Bring the dashboard to the front to clear all other activities
		i = new Intent(this, DashboardActivity.class);
		i.setFlags(FLAG_ACTIVITY_NEW_TASK | FLAG_ACTIVITY_CLEAR_TOP);
		i.putExtra("briar.STARTUP_FAILED", true);
		startActivity(i);
	}

	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		return START_NOT_STICKY; // Don't restart automatically if killed
	}

	@Override
	public IBinder onBind(Intent intent) {
		return binder;
	}

	@Override
	public void onDestroy() {
		super.onDestroy();
		LOG.info("Destroyed");
		stopForeground(true);
		notificationManager.clearNotifications();
		// Stop the services in a background thread
		new Thread() {
			@Override
			public void run() {
				if (started) {
					eventBus.removeListener(BriarService.this);
					lifecycleManager.stopServices();
				}
				androidExecutor.shutdown();
			}
		}.start();
	}

	@Override
	public void onLowMemory() {
		super.onLowMemory();
		LOG.warning("Memory is low");
		// FIXME: Work out what to do about it
	}

	public void eventOccurred(Event e) {
		if (e instanceof MessageAddedEvent) {
			MessageAddedEvent m = (MessageAddedEvent) e;
			GroupId g = m.getGroup().getId();
			ContactId c = m.getContactId();
			if (c != null) showMessageNotification(g, c);
		}
	}

	private void showMessageNotification(final GroupId g, final ContactId c) {
		dbExecutor.execute(new Runnable() {
			public void run() {
				try {
					lifecycleManager.waitForDatabase();
					if (g.equals(db.getInboxGroupId(c)))
						notificationManager.showPrivateMessageNotification(c);
					else notificationManager.showForumPostNotification(g);
				} catch (DbException e) {
					if (LOG.isLoggable(WARNING))
						LOG.log(WARNING, e.toString(), e);
				} catch (InterruptedException e) {
					LOG.info("Interruped while waiting for database");
					Thread.currentThread().interrupt();
				}
			}
		});
	}

	/** Waits for the database to be opened before returning. */
	public void waitForDatabase() throws InterruptedException {
		lifecycleManager.waitForDatabase();
	}

	/** Waits for all services to start before returning. */
	public void waitForStartup() throws InterruptedException {
		lifecycleManager.waitForStartup();
	}

	/** Waits for all services to stop before returning. */
	public void waitForShutdown() throws InterruptedException {
		lifecycleManager.waitForShutdown();
	}

	/** Starts the shutdown process. */
	public void shutdown() {
		stopSelf(); // This will call onDestroy()
	}

	public class BriarBinder extends Binder {

		/** Returns the bound service. */
		public BriarService getService() {
			return BriarService.this;
		}
	}

	public static class BriarServiceConnection implements ServiceConnection {

		private final CountDownLatch binderLatch = new CountDownLatch(1);

		private volatile IBinder binder = null;

		public void onServiceConnected(ComponentName name, IBinder binder) {
			this.binder = binder;
			binderLatch.countDown();
		}

		public void onServiceDisconnected(ComponentName name) {}

		/** Waits for the service to connect and returns its binder. */
		public IBinder waitForBinder() throws InterruptedException {
			binderLatch.await();
			return binder;
		}
	}
}
