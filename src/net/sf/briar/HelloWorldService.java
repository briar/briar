package net.sf.briar;

import static java.util.logging.Level.INFO;
import static java.util.logging.Level.WARNING;

import java.io.IOException;
import java.util.logging.Logger;

import net.sf.briar.android.AndroidModule;
import net.sf.briar.api.crypto.KeyManager;
import net.sf.briar.api.db.DatabaseComponent;
import net.sf.briar.api.db.DbException;
import net.sf.briar.api.plugins.PluginManager;
import net.sf.briar.clock.ClockModule;
import net.sf.briar.crypto.CryptoModule;
import net.sf.briar.db.DatabaseModule;
import net.sf.briar.lifecycle.LifecycleModule;
import net.sf.briar.plugins.PluginsModule;
import net.sf.briar.protocol.ProtocolModule;
import net.sf.briar.protocol.duplex.DuplexProtocolModule;
import net.sf.briar.protocol.simplex.SimplexProtocolModule;
import net.sf.briar.serial.SerialModule;
import net.sf.briar.transport.TransportModule;
import android.app.Service;
import android.content.Intent;
import android.os.IBinder;

import com.google.inject.Guice;
import com.google.inject.Injector;

public class HelloWorldService extends Service implements Runnable {

	private static final Logger LOG =
			Logger.getLogger(HelloWorldService.class.getName());

	private DatabaseComponent db = null;
	private KeyManager keyManager = null;
	private PluginManager pluginManager = null;

	@Override
	public void onCreate() {
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

	public void run() {
		Injector i = Guice.createInjector(
				new HelloWorldModule(getApplicationContext()),
				new AndroidModule(), new ClockModule(), new CryptoModule(),
				new DatabaseModule(), new LifecycleModule(),
				new PluginsModule(), new ProtocolModule(),
				new DuplexProtocolModule(), new SimplexProtocolModule(),
				new SerialModule(), new TransportModule());
		db = i.getInstance(DatabaseComponent.class);
		keyManager = i.getInstance(KeyManager.class);
		pluginManager = i.getInstance(PluginManager.class);
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
				Thread.sleep(1000);
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
	}
}
