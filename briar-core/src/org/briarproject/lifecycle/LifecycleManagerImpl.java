package org.briarproject.lifecycle;

import static java.util.logging.Level.INFO;
import static java.util.logging.Level.WARNING;
import static org.briarproject.api.lifecycle.LifecycleManager.StartResult.ALREADY_RUNNING;
import static org.briarproject.api.lifecycle.LifecycleManager.StartResult.DB_ERROR;
import static org.briarproject.api.lifecycle.LifecycleManager.StartResult.SERVICE_ERROR;
import static org.briarproject.api.lifecycle.LifecycleManager.StartResult.SUCCESS;

import java.io.IOException;
import java.util.Collection;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Semaphore;
import java.util.logging.Logger;

import javax.inject.Inject;

import org.briarproject.api.db.DatabaseComponent;
import org.briarproject.api.db.DbException;
import org.briarproject.api.event.EventBus;
import org.briarproject.api.event.ShutdownEvent;
import org.briarproject.api.lifecycle.LifecycleManager;
import org.briarproject.api.lifecycle.Service;
import org.briarproject.api.system.Clock;

class LifecycleManagerImpl implements LifecycleManager {

	private static final Logger LOG =
			Logger.getLogger(LifecycleManagerImpl.class.getName());

	private final Clock clock;
	private final DatabaseComponent db;
	private final EventBus eventBus;
	private final Collection<Service> services;
	private final Collection<ExecutorService> executors;
	private final Semaphore startStopSemaphore = new Semaphore(1);
	private final CountDownLatch dbLatch = new CountDownLatch(1);
	private final CountDownLatch startupLatch = new CountDownLatch(1);
	private final CountDownLatch shutdownLatch = new CountDownLatch(1);

	@Inject
	LifecycleManagerImpl(Clock clock, DatabaseComponent db, EventBus eventBus) {
		this.clock = clock;
		this.db = db;
		this.eventBus = eventBus;
		services = new CopyOnWriteArrayList<Service>();
		executors = new CopyOnWriteArrayList<ExecutorService>();
	}

	public void register(Service s) {
		if (LOG.isLoggable(INFO))
			LOG.info("Registering service " + s.getClass().getName());
		services.add(s);
	}

	public void registerForShutdown(ExecutorService e) {
		if (LOG.isLoggable(INFO))
			LOG.info("Registering executor " + e.getClass().getName());
		executors.add(e);
	}

	public StartResult startServices() {
		if (!startStopSemaphore.tryAcquire()) {
			LOG.info("Already starting or stopping");
			return ALREADY_RUNNING;
		}
		try {
			LOG.info("Starting services");
			long now = clock.currentTimeMillis();
			boolean reopened = db.open();
			long duration = clock.currentTimeMillis() - now;
			if (LOG.isLoggable(INFO)) {
				if (reopened)
					LOG.info("Reopening database took " + duration + " ms");
				else LOG.info("Creating database took " + duration + " ms");
			}
			dbLatch.countDown();
			for (Service s : services) {
				now = clock.currentTimeMillis();
				boolean started = s.start();
				duration = clock.currentTimeMillis() - now;
				if (!started) {
					if (LOG.isLoggable(WARNING)) {
						String name = s.getClass().getName();
						LOG.warning(name + " did not start");
					}
					return SERVICE_ERROR;
				}
				if (LOG.isLoggable(INFO)) {
					String name = s.getClass().getName();
					LOG.info("Starting " + name + " took " + duration + " ms");
				}
			}
			startupLatch.countDown();
			return SUCCESS;
		} catch (DbException e) {
			if (LOG.isLoggable(WARNING)) LOG.log(WARNING, e.toString(), e);
			return DB_ERROR;
		} catch (IOException e) {
			if (LOG.isLoggable(WARNING)) LOG.log(WARNING, e.toString(), e);
			return DB_ERROR;
		} finally {
			startStopSemaphore.release();
		}
	}

	public void stopServices() {
		try {
			startStopSemaphore.acquire();
		} catch (InterruptedException e) {
			LOG.warning("Interrupted while waiting to stop services");
			return;
		}
		try {
			LOG.info("Stopping services");
			eventBus.broadcast(new ShutdownEvent());
			for (Service s : services) {
				boolean stopped = s.stop();
				if (LOG.isLoggable(INFO)) {
					String name = s.getClass().getName();
					if (stopped) LOG.info("Service stopped: " + name);
					else LOG.warning("Service failed to stop: " + name);
				}
			}
			for (ExecutorService e : executors) e.shutdownNow();
			if (LOG.isLoggable(INFO))
				LOG.info(executors.size() + " executors shut down");
			db.close();
			LOG.info("Database closed");
			shutdownLatch.countDown();
		} catch (DbException e) {
			if (LOG.isLoggable(WARNING)) LOG.log(WARNING, e.toString(), e);
		} catch (IOException e) {
			if (LOG.isLoggable(WARNING)) LOG.log(WARNING, e.toString(), e);
		} finally {
			startStopSemaphore.release();
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
