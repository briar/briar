package net.sf.briar.android.helloworld;

import static java.util.logging.Level.INFO;
import static java.util.logging.Level.WARNING;

import java.io.IOException;
import java.util.logging.Logger;

import net.sf.briar.api.crypto.KeyManager;
import net.sf.briar.api.db.DatabaseComponent;
import net.sf.briar.api.db.DbException;
import net.sf.briar.api.plugins.PluginManager;
import roboguice.service.RoboService;
import android.content.Intent;
import android.os.IBinder;

import com.google.inject.Inject;

public class HelloWorldService extends RoboService implements Runnable {

	private static final Logger LOG =
			Logger.getLogger(HelloWorldService.class.getName());

	@Inject private DatabaseComponent db;
	@Inject private KeyManager keyManager;
	@Inject private PluginManager pluginManager;

	@Override
	public void onCreate() {
		super.onCreate();
		if(LOG.isLoggable(INFO)) LOG.info("Created");
		Thread t = new Thread(this);
		t.setDaemon(false);
		t.start();
	}

	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		return 0;
	}

	@Override
	public IBinder onBind(Intent intent) {
		return null;
	}

	@Override
	public void onDestroy() {
		super.onDestroy();
		if(LOG.isLoggable(INFO)) LOG.info("Destroyed");
	}

	public void run() {
		try {
			// Start...
			if(LOG.isLoggable(INFO)) LOG.info("Starting");
			db.open(false);
			if(LOG.isLoggable(INFO)) LOG.info("Database opened");
			keyManager.start();
			if(LOG.isLoggable(INFO)) LOG.info("Key manager started");
			int pluginsStarted = pluginManager.start(this);
			if(LOG.isLoggable(INFO))
				LOG.info(pluginsStarted + " plugins started");
			// ...sleep...
			try {
				Thread.sleep(30 * 1000);
			} catch(InterruptedException ignored) {}
			// ...and stop
			if(LOG.isLoggable(INFO)) LOG.info("Shutting down");
			int pluginsStopped = pluginManager.stop();
			if(LOG.isLoggable(INFO))
				LOG.info(pluginsStopped + " plugins stopped");
			keyManager.stop();
			if(LOG.isLoggable(INFO)) LOG.info("Key manager stopped");
			db.close();
			if(LOG.isLoggable(INFO)) LOG.info("Database closed");
		} catch(DbException e) {
			if(LOG.isLoggable(WARNING)) LOG.warning(e.toString());
		} catch(IOException e) {
			if(LOG.isLoggable(WARNING)) LOG.warning(e.toString());
		}
		stopSelf();
	}
}
