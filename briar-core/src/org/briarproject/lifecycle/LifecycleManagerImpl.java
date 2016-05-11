package org.briarproject.lifecycle;

import org.briarproject.api.clients.Client;
import org.briarproject.api.db.DatabaseComponent;
import org.briarproject.api.db.DbException;
import org.briarproject.api.db.Transaction;
import org.briarproject.api.event.EventBus;
import org.briarproject.api.event.ShutdownEvent;
import org.briarproject.api.lifecycle.LifecycleManager;
import org.briarproject.api.lifecycle.Service;
import org.briarproject.api.lifecycle.ServiceException;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Semaphore;
import java.util.logging.Logger;

import javax.inject.Inject;

import static java.util.logging.Level.INFO;
import static java.util.logging.Level.WARNING;
import static org.briarproject.api.lifecycle.LifecycleManager.StartResult.ALREADY_RUNNING;
import static org.briarproject.api.lifecycle.LifecycleManager.StartResult.DB_ERROR;
import static org.briarproject.api.lifecycle.LifecycleManager.StartResult.SERVICE_ERROR;
import static org.briarproject.api.lifecycle.LifecycleManager.StartResult.SUCCESS;

class LifecycleManagerImpl implements LifecycleManager {

	private static final Logger LOG =
			Logger.getLogger(LifecycleManagerImpl.class.getName());

	private final DatabaseComponent db;
	private final EventBus eventBus;
	private final List<Service> services;
	private final List<Client> clients;
	private final List<ExecutorService> executors;
	private final Semaphore startStopSemaphore = new Semaphore(1);
	private final CountDownLatch dbLatch = new CountDownLatch(1);
	private final CountDownLatch startupLatch = new CountDownLatch(1);
	private final CountDownLatch shutdownLatch = new CountDownLatch(1);

	@Inject
	LifecycleManagerImpl(DatabaseComponent db, EventBus eventBus) {
		this.db = db;
		this.eventBus = eventBus;
		services = new CopyOnWriteArrayList<Service>();
		clients = new CopyOnWriteArrayList<Client>();
		executors = new CopyOnWriteArrayList<ExecutorService>();
	}

	@Override
	public void registerService(Service s) {
		if (LOG.isLoggable(INFO))
			LOG.info("Registering service " + s.getClass().getName());
		services.add(s);
	}

	@Override
	public void registerClient(Client c) {
		if (LOG.isLoggable(INFO))
			LOG.info("Registering client " + c.getClass().getName());
		clients.add(c);
	}

	@Override
	public void registerForShutdown(ExecutorService e) {
		LOG.info("Registering executor");
		executors.add(e);
	}

	@Override
	public StartResult startServices() {
		if (!startStopSemaphore.tryAcquire()) {
			LOG.info("Already starting or stopping");
			return ALREADY_RUNNING;
		}
		try {
			LOG.info("Starting services");
			long start = System.currentTimeMillis();
			boolean reopened = db.open();
			long duration = System.currentTimeMillis() - start;
			if (LOG.isLoggable(INFO)) {
				if (reopened)
					LOG.info("Reopening database took " + duration + " ms");
				else LOG.info("Creating database took " + duration + " ms");
			}
			dbLatch.countDown();
			Transaction txn = db.startTransaction(false);
			try {
				for (Client c : clients) {
					start = System.currentTimeMillis();
					c.createLocalState(txn);
					duration = System.currentTimeMillis() - start;
					if (LOG.isLoggable(INFO)) {
						LOG.info("Starting client " + c.getClass().getName()
								+ " took " + duration + " ms");
					}
				}
				txn.setComplete();
			} finally {
				db.endTransaction(txn);
			}
			for (Service s : services) {
				start = System.currentTimeMillis();
				s.startService();
				duration = System.currentTimeMillis() - start;
				if (LOG.isLoggable(INFO)) {
					LOG.info("Starting service " + s.getClass().getName()
							+ " took " + duration + " ms");
				}
			}
			startupLatch.countDown();
			return SUCCESS;
		} catch (DbException e) {
			if (LOG.isLoggable(WARNING)) LOG.log(WARNING, e.toString(), e);
			return DB_ERROR;
		} catch (ServiceException e) {
			if (LOG.isLoggable(WARNING)) LOG.log(WARNING, e.toString(), e);
			return SERVICE_ERROR;
		} finally {
			startStopSemaphore.release();
		}
	}

	@Override
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
				long start = System.currentTimeMillis();
				s.stopService();
				long duration = System.currentTimeMillis() - start;
				if (LOG.isLoggable(INFO)) {
					LOG.info("Stopping service " + s.getClass().getName()
							+ " took " + duration + " ms");
				}
			}
			for (ExecutorService e : executors) e.shutdownNow();
			if (LOG.isLoggable(INFO))
				LOG.info(executors.size() + " executors shut down");
			long start = System.currentTimeMillis();
			db.close();
			long duration = System.currentTimeMillis() - start;
			if (LOG.isLoggable(INFO))
				LOG.info("Closing database took " + duration + " ms");
			shutdownLatch.countDown();
		} catch (DbException e) {
			if (LOG.isLoggable(WARNING)) LOG.log(WARNING, e.toString(), e);
		} catch (ServiceException e) {
			if (LOG.isLoggable(WARNING)) LOG.log(WARNING, e.toString(), e);
		} finally {
			startStopSemaphore.release();
		}
	}

	@Override
	public void waitForDatabase() throws InterruptedException {
		dbLatch.await();
	}

	@Override
	public void waitForStartup() throws InterruptedException {
		startupLatch.await();
	}

	@Override
	public void waitForShutdown() throws InterruptedException {
		shutdownLatch.await();
	}
}
