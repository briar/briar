package net.sf.briar.lifecycle;

import static java.util.logging.Level.INFO;
import static java.util.logging.Level.WARNING;

import java.io.IOException;
import java.util.Collection;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.logging.Logger;

import javax.inject.Inject;

import net.sf.briar.api.db.DatabaseComponent;
import net.sf.briar.api.db.DbException;
import net.sf.briar.api.lifecycle.LifecycleManager;
import net.sf.briar.api.lifecycle.Service;

class LifecycleManagerImpl implements LifecycleManager {

	private static final Logger LOG =
			Logger.getLogger(LifecycleManagerImpl.class.getName());

	private final DatabaseComponent db;
	private final Collection<Service> services;
	private final Collection<ExecutorService> executors;
	private final CountDownLatch dbLatch = new CountDownLatch(1);
	private final CountDownLatch startupLatch = new CountDownLatch(1);
	private final CountDownLatch shutdownLatch = new CountDownLatch(1);

	@Inject
	LifecycleManagerImpl(DatabaseComponent db) {
		this.db = db;
		services = new CopyOnWriteArrayList<Service>();
		executors = new CopyOnWriteArrayList<ExecutorService>();
	}

	public void register(Service s) {
		if(LOG.isLoggable(INFO))
			LOG.info("Registering service " + s.getClass().getName());
		services.add(s);
	}

	public void registerForShutdown(ExecutorService e) {
		if(LOG.isLoggable(INFO))
			LOG.info("Registering executor " + e.getClass().getName());
		executors.add(e);
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
			for(Service s : services) {
				boolean started = s.start();
				if(LOG.isLoggable(INFO)) {
					String name = s.getClass().getName();
					if(started) LOG.info("Service started: " + name);
					else LOG.info("Service failed to start: " + name);
				}
			}
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
			for(Service s : services) {
				boolean stopped = s.stop();
				if(LOG.isLoggable(INFO)) {
					String name = s.getClass().getName();
					if(stopped) LOG.info("Service stopped: " + name);
					else LOG.warning("Service failed to stop: " + name);
				}
			}
			for(ExecutorService e : executors) e.shutdownNow();
			if(LOG.isLoggable(INFO))
				LOG.info(executors.size() + " executors shut down");
			db.close();
			if(LOG.isLoggable(INFO)) LOG.info("Database closed");
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
