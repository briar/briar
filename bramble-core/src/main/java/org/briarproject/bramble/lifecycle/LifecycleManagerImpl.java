package org.briarproject.bramble.lifecycle;

import org.briarproject.bramble.api.crypto.CryptoComponent;
import org.briarproject.bramble.api.crypto.KeyPair;
import org.briarproject.bramble.api.db.DataTooNewException;
import org.briarproject.bramble.api.db.DataTooOldException;
import org.briarproject.bramble.api.db.DatabaseComponent;
import org.briarproject.bramble.api.db.DbException;
import org.briarproject.bramble.api.db.MigrationListener;
import org.briarproject.bramble.api.db.Transaction;
import org.briarproject.bramble.api.event.EventBus;
import org.briarproject.bramble.api.identity.AuthorFactory;
import org.briarproject.bramble.api.identity.IdentityManager;
import org.briarproject.bramble.api.identity.LocalAuthor;
import org.briarproject.bramble.api.lifecycle.LifecycleManager;
import org.briarproject.bramble.api.lifecycle.Service;
import org.briarproject.bramble.api.lifecycle.ServiceException;
import org.briarproject.bramble.api.lifecycle.event.LifecycleEvent;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.sync.Client;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Semaphore;
import java.util.logging.Logger;

import javax.annotation.Nullable;
import javax.annotation.concurrent.ThreadSafe;
import javax.inject.Inject;

import static java.util.logging.Level.FINE;
import static java.util.logging.Level.INFO;
import static java.util.logging.Level.WARNING;
import static org.briarproject.bramble.api.lifecycle.LifecycleManager.LifecycleState.MIGRATING_DATABASE;
import static org.briarproject.bramble.api.lifecycle.LifecycleManager.LifecycleState.RUNNING;
import static org.briarproject.bramble.api.lifecycle.LifecycleManager.LifecycleState.STARTING;
import static org.briarproject.bramble.api.lifecycle.LifecycleManager.LifecycleState.STARTING_SERVICES;
import static org.briarproject.bramble.api.lifecycle.LifecycleManager.LifecycleState.STOPPING;
import static org.briarproject.bramble.api.lifecycle.LifecycleManager.StartResult.ALREADY_RUNNING;
import static org.briarproject.bramble.api.lifecycle.LifecycleManager.StartResult.DATA_TOO_NEW_ERROR;
import static org.briarproject.bramble.api.lifecycle.LifecycleManager.StartResult.DATA_TOO_OLD_ERROR;
import static org.briarproject.bramble.api.lifecycle.LifecycleManager.StartResult.DB_ERROR;
import static org.briarproject.bramble.api.lifecycle.LifecycleManager.StartResult.SERVICE_ERROR;
import static org.briarproject.bramble.api.lifecycle.LifecycleManager.StartResult.SUCCESS;
import static org.briarproject.bramble.util.LogUtils.logDuration;
import static org.briarproject.bramble.util.LogUtils.logException;
import static org.briarproject.bramble.util.LogUtils.now;

@ThreadSafe
@NotNullByDefault
class LifecycleManagerImpl implements LifecycleManager, MigrationListener {

	private static final Logger LOG =
			Logger.getLogger(LifecycleManagerImpl.class.getName());

	private final DatabaseComponent db;
	private final EventBus eventBus;
	private final List<Service> services;
	private final List<Client> clients;
	private final List<ExecutorService> executors;
	private final CryptoComponent crypto;
	private final AuthorFactory authorFactory;
	private final IdentityManager identityManager;
	private final Semaphore startStopSemaphore = new Semaphore(1);
	private final CountDownLatch dbLatch = new CountDownLatch(1);
	private final CountDownLatch startupLatch = new CountDownLatch(1);
	private final CountDownLatch shutdownLatch = new CountDownLatch(1);

	private volatile LifecycleState state = STARTING;

	@Inject
	LifecycleManagerImpl(DatabaseComponent db, EventBus eventBus,
			CryptoComponent crypto, AuthorFactory authorFactory,
			IdentityManager identityManager) {
		this.db = db;
		this.eventBus = eventBus;
		this.crypto = crypto;
		this.authorFactory = authorFactory;
		this.identityManager = identityManager;
		services = new CopyOnWriteArrayList<>();
		clients = new CopyOnWriteArrayList<>();
		executors = new CopyOnWriteArrayList<>();
	}

	@Override
	public void registerService(Service s) {
		if (LOG.isLoggable(INFO))
			LOG.info("Registering service " + s.getClass().getSimpleName());
		services.add(s);
	}

	@Override
	public void registerClient(Client c) {
		if (LOG.isLoggable(INFO))
			LOG.info("Registering client " + c.getClass().getSimpleName());
		clients.add(c);
	}

	@Override
	public void registerForShutdown(ExecutorService e) {
		LOG.info("Registering executor " + e.getClass().getSimpleName());
		executors.add(e);
	}

	private LocalAuthor createLocalAuthor(String nickname) {
		long start = now();
		KeyPair keyPair = crypto.generateSignatureKeyPair();
		byte[] publicKey = keyPair.getPublic().getEncoded();
		byte[] privateKey = keyPair.getPrivate().getEncoded();
		LocalAuthor localAuthor = authorFactory
				.createLocalAuthor(nickname, publicKey, privateKey);
		logDuration(LOG, "Creating local author", start);
		return localAuthor;
	}

	private void registerLocalAuthor(LocalAuthor author) throws DbException {
		long start = now();
		identityManager.registerLocalAuthor(author);
		logDuration(LOG, "Registering local author", start);
	}

	@Override
	public StartResult startServices(@Nullable String nickname) {
		if (!startStopSemaphore.tryAcquire()) {
			LOG.info("Already starting or stopping");
			return ALREADY_RUNNING;
		}
		try {
			LOG.info("Starting services");
			long start = now();

			boolean reopened = db.open(this);
			if (reopened) logDuration(LOG, "Reopening database", start);
			else logDuration(LOG, "Creating database", start);

			if (nickname != null) {
				registerLocalAuthor(createLocalAuthor(nickname));
			}

			state = STARTING_SERVICES;
			dbLatch.countDown();
			eventBus.broadcast(new LifecycleEvent(STARTING_SERVICES));

			Transaction txn = db.startTransaction(false);
			try {
				for (Client c : clients) {
					start = now();
					c.createLocalState(txn);
					if (LOG.isLoggable(FINE)) {
						logDuration(LOG, "Starting client "
								+ c.getClass().getSimpleName(), start);
					}
				}
				db.commitTransaction(txn);
			} finally {
				db.endTransaction(txn);
			}
			for (Service s : services) {
				start = now();
				s.startService();
				if (LOG.isLoggable(FINE)) {
					logDuration(LOG, "Starting service "
							+ s.getClass().getSimpleName(), start);
				}
			}

			state = RUNNING;
			startupLatch.countDown();
			eventBus.broadcast(new LifecycleEvent(RUNNING));
			return SUCCESS;
		} catch (DataTooOldException e) {
			logException(LOG, WARNING, e);
			return DATA_TOO_OLD_ERROR;
		} catch (DataTooNewException e) {
			logException(LOG, WARNING, e);
			return DATA_TOO_NEW_ERROR;
		} catch (DbException e) {
			logException(LOG, WARNING, e);
			return DB_ERROR;
		} catch (ServiceException e) {
			logException(LOG, WARNING, e);
			return SERVICE_ERROR;
		} finally {
			startStopSemaphore.release();
		}
	}

	@Override
	public void onMigrationRun() {
		state = MIGRATING_DATABASE;
		eventBus.broadcast(new LifecycleEvent(MIGRATING_DATABASE));
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
			state = STOPPING;
			eventBus.broadcast(new LifecycleEvent(STOPPING));
			for (Service s : services) {
				long start = now();
				s.stopService();
				if (LOG.isLoggable(FINE)) {
					logDuration(LOG, "Stopping service "
							+ s.getClass().getSimpleName(), start);
				}
			}
			for (ExecutorService e : executors) {
				if (LOG.isLoggable(FINE)) {
					LOG.fine("Stopping executor "
							+ e.getClass().getSimpleName());
				}
				e.shutdownNow();
			}
			long start = now();
			db.close();
			logDuration(LOG, "Closing database", start);
			shutdownLatch.countDown();
		} catch (DbException | ServiceException e) {
			logException(LOG, WARNING, e);
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

	@Override
	public LifecycleState getLifecycleState() {
		return state;
	}
}
