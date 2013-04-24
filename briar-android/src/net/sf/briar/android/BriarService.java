package net.sf.briar.android;

import static android.app.PendingIntent.FLAG_ONE_SHOT;
import static android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP;
import static java.util.logging.Level.INFO;
import static java.util.logging.Level.WARNING;

import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.logging.Logger;

import net.sf.briar.R;
import net.sf.briar.api.crypto.KeyManager;
import net.sf.briar.api.db.DatabaseComponent;
import net.sf.briar.api.db.DatabaseConfig;
import net.sf.briar.api.db.DbException;
import net.sf.briar.api.plugins.PluginManager;
import roboguice.service.RoboService;
import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Binder;
import android.os.IBinder;
import android.support.v4.app.NotificationCompat;

import com.google.inject.Inject;

public class BriarService extends RoboService {

	private static final Logger LOG =
			Logger.getLogger(BriarService.class.getName());

	private final CountDownLatch startupLatch = new CountDownLatch(1);
	private final CountDownLatch shutdownLatch = new CountDownLatch(1);
	private final Binder binder = new BriarBinder();

	@Inject private DatabaseConfig databaseConfig = null;
	@Inject private DatabaseComponent db = null;
	@Inject private KeyManager keyManager = null;
	@Inject private PluginManager pluginManager = null;

	@Override
	public void onCreate() {
		super.onCreate();
		if(LOG.isLoggable(INFO)) LOG.info("Created");
		// Show an ongoing notification that the service is running
		NotificationCompat.Builder b = new NotificationCompat.Builder(this);
		b.setSmallIcon(R.drawable.notification_icon);
		b.setContentTitle(getText(R.string.notification_title));
		b.setContentText(getText(R.string.notification_text));
		b.setWhen(0); // Don't show the time
		// Touch the notification to quit
		Intent i = new Intent(this, HomeScreenActivity.class);
		i.addFlags(FLAG_ACTIVITY_CLEAR_TOP);
		i.putExtra("net.sf.briar.QUIT", true);
		PendingIntent pi = PendingIntent.getActivity(this, 0, i, FLAG_ONE_SHOT);
		b.setContentIntent(pi);
		b.setOngoing(true);
		startForeground(1, b.build());
		// Start the services in a background thread
		new Thread() {
			@Override
			public void run() {
				startServices();
			}
		}.start();
	}

	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		if(LOG.isLoggable(INFO)) LOG.info("Started");
		return START_STICKY;
	}

	@Override
	public IBinder onBind(Intent intent) {
		return binder;
	}

	@Override
	public void onDestroy() {
		super.onDestroy();
		if(LOG.isLoggable(INFO)) LOG.info("Destroyed");
		// Stop the services in a background thread
		new Thread() {
			@Override
			public void run() {
				stopServices();
			}
		}.start();
	}

	private void startServices() {
		if(databaseConfig.getEncryptionKey() == null)
			throw new IllegalStateException();
		try {
			if(LOG.isLoggable(INFO)) LOG.info("Starting");
			boolean reopened = db.open();
			if(LOG.isLoggable(INFO)) {
				if(reopened) LOG.info("Database reopened");
				else LOG.info("Database created");
			}
			keyManager.start();
			if(LOG.isLoggable(INFO)) LOG.info("Key manager started");
			int pluginsStarted = pluginManager.start();
			if(LOG.isLoggable(INFO))
				LOG.info(pluginsStarted + " plugins started");
			startupLatch.countDown();
		} catch(DbException e) {
			if(LOG.isLoggable(WARNING)) LOG.log(WARNING, e.toString(), e);
		} catch(IOException e) {
			if(LOG.isLoggable(WARNING)) LOG.log(WARNING, e.toString(), e);
		}
	}

	private void stopServices() {
		try {
			if(LOG.isLoggable(INFO)) LOG.info("Shutting down");
			int pluginsStopped = pluginManager.stop();
			if(LOG.isLoggable(INFO))
				LOG.info(pluginsStopped + " plugins stopped");
			keyManager.stop();
			if(LOG.isLoggable(INFO)) LOG.info("Key manager stopped");
			db.close();
			if(LOG.isLoggable(INFO)) LOG.info("Database closed");
			shutdownLatch.countDown();
		} catch(DbException e) {
			if(LOG.isLoggable(WARNING)) LOG.log(WARNING, e.toString(), e);
		} catch(IOException e) {
			if(LOG.isLoggable(WARNING)) LOG.log(WARNING, e.toString(), e);
		}
	}

	public void waitForStartup() throws InterruptedException {
		startupLatch.await();
	}

	public void waitForShutdown() throws InterruptedException {
		shutdownLatch.await();
	}

	public void shutdown() {
		stopSelf();
	}

	public class BriarBinder extends Binder {

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

		public IBinder waitForBinder() throws InterruptedException {
			binderLatch.await();
			return binder;
		}

		public void waitForStartup() throws InterruptedException {
			waitForBinder();
			((BriarBinder) binder).getService().waitForStartup();
		}
	}
}
