package net.sf.briar.lifecycle;

import static java.util.logging.Level.INFO;
import static java.util.logging.Level.WARNING;

import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.logging.Logger;

import net.sf.briar.api.crypto.CryptoExecutor;
import net.sf.briar.api.crypto.KeyManager;
import net.sf.briar.api.db.DatabaseComponent;
import net.sf.briar.api.db.DatabaseExecutor;
import net.sf.briar.api.db.DbException;
import net.sf.briar.api.lifecycle.LifecycleManager;
import net.sf.briar.api.plugins.PluginExecutor;
import net.sf.briar.api.plugins.PluginManager;
import net.sf.briar.api.reliability.ReliabilityExecutor;
import net.sf.briar.api.transport.IncomingConnectionExecutor;

import com.google.inject.Inject;

class LifecycleManagerImpl implements LifecycleManager {

	private static final Logger LOG =
			Logger.getLogger(LifecycleManagerImpl.class.getName());

	private final DatabaseComponent db;
	private final KeyManager keyManager;
	private final PluginManager pluginManager;
	private final ExecutorService cryptoExecutor;
	private final ExecutorService dbExecutor;
	private final ExecutorService connExecutor;
	private final ExecutorService pluginExecutor;
	private final ExecutorService reliabilityExecutor;
	private final CountDownLatch dbLatch = new CountDownLatch(1);
	private final CountDownLatch startupLatch = new CountDownLatch(1);
	private final CountDownLatch shutdownLatch = new CountDownLatch(1);

	@Inject
	LifecycleManagerImpl(DatabaseComponent db, KeyManager keyManager,
			PluginManager pluginManager,
			@CryptoExecutor ExecutorService cryptoExecutor,
			@DatabaseExecutor ExecutorService dbExecutor,
			@IncomingConnectionExecutor ExecutorService connExecutor,
			@PluginExecutor ExecutorService pluginExecutor,
			@ReliabilityExecutor ExecutorService reliabilityExecutor) {
		this.db = db;
		this.keyManager = keyManager;
		this.pluginManager = pluginManager;
		this.cryptoExecutor = cryptoExecutor;
		this.dbExecutor = dbExecutor;
		this.connExecutor = connExecutor;
		this.pluginExecutor = pluginExecutor;
		this.reliabilityExecutor = reliabilityExecutor;
	}

	public void startServices() {
		try {
			if(LOG.isLoggable(INFO)) LOG.info("Starting");
			boolean reopened = db.open();
			if(LOG.isLoggable(INFO)) {
				if(reopened) LOG.info("Database reopened");
				else LOG.info("Database created");
			}
			dbLatch.countDown();
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

	public void stopServices() {
		try {
			if(LOG.isLoggable(INFO)) LOG.info("Shutting down");
			int pluginsStopped = pluginManager.stop();
			if(LOG.isLoggable(INFO))
				LOG.info(pluginsStopped + " plugins stopped");
			keyManager.stop();
			if(LOG.isLoggable(INFO)) LOG.info("Key manager stopped");
			db.close();
			if(LOG.isLoggable(INFO)) LOG.info("Database closed");
			cryptoExecutor.shutdownNow();
			dbExecutor.shutdownNow();
			connExecutor.shutdownNow();
			pluginExecutor.shutdownNow();
			reliabilityExecutor.shutdownNow();
			if(LOG.isLoggable(INFO)) LOG.info("Executors shut down");
			shutdownLatch.countDown();
		} catch(DbException e) {
			if(LOG.isLoggable(WARNING)) LOG.log(WARNING, e.toString(), e);
		} catch(IOException e) {
			if(LOG.isLoggable(WARNING)) LOG.log(WARNING, e.toString(), e);
		}
	}

	public void waitForDatabase() throws InterruptedException {
		dbLatch.await();
	}

	public void waitForStartup() throws InterruptedException {
		startupLatch.await();
	}

	public void waitForShutdown() throws InterruptedException {
		shutdownLatch.await();
	}
}
