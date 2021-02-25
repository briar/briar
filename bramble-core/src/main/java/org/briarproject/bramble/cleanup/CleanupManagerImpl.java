package org.briarproject.bramble.cleanup;

import org.briarproject.bramble.api.cleanup.CleanupHook;
import org.briarproject.bramble.api.cleanup.CleanupManager;
import org.briarproject.bramble.api.cleanup.event.CleanupTimerStartedEvent;
import org.briarproject.bramble.api.cleanup.event.MessagesCleanedUpEvent;
import org.briarproject.bramble.api.db.DatabaseComponent;
import org.briarproject.bramble.api.db.DatabaseExecutor;
import org.briarproject.bramble.api.db.DbException;
import org.briarproject.bramble.api.db.Transaction;
import org.briarproject.bramble.api.event.Event;
import org.briarproject.bramble.api.event.EventListener;
import org.briarproject.bramble.api.lifecycle.Service;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.sync.ClientId;
import org.briarproject.bramble.api.sync.Group;
import org.briarproject.bramble.api.sync.GroupId;
import org.briarproject.bramble.api.sync.MessageId;
import org.briarproject.bramble.api.system.Clock;
import org.briarproject.bramble.api.system.TaskScheduler;
import org.briarproject.bramble.api.versioning.ClientMajorVersion;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.logging.Logger;

import javax.annotation.concurrent.GuardedBy;
import javax.annotation.concurrent.ThreadSafe;
import javax.inject.Inject;

import static java.lang.Math.max;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.logging.Level.INFO;
import static java.util.logging.Level.WARNING;
import static java.util.logging.Logger.getLogger;
import static org.briarproject.bramble.api.db.DatabaseComponent.NO_CLEANUP_DEADLINE;
import static org.briarproject.bramble.util.LogUtils.logException;

@ThreadSafe
@NotNullByDefault
class CleanupManagerImpl implements CleanupManager, Service, EventListener {

	private static final Logger LOG =
			getLogger(CleanupManagerImpl.class.getName());

	private final Executor dbExecutor;
	private final DatabaseComponent db;
	private final TaskScheduler taskScheduler;
	private final Clock clock;
	private final Map<ClientMajorVersion, CleanupHook> hooks =
			new ConcurrentHashMap<>();
	private final Object lock = new Object();

	@GuardedBy("lock")
	private final Set<CleanupTask> pending = new HashSet<>();

	@Inject
	CleanupManagerImpl(@DatabaseExecutor Executor dbExecutor,
			DatabaseComponent db, TaskScheduler taskScheduler, Clock clock) {
		this.dbExecutor = dbExecutor;
		this.db = db;
		this.taskScheduler = taskScheduler;
		this.clock = clock;
	}

	@Override
	public void registerCleanupHook(ClientId c, int majorVersion,
			CleanupHook hook) {
		hooks.put(new ClientMajorVersion(c, majorVersion), hook);
	}

	@Override
	public void startService() {
		maybeScheduleTask(clock.currentTimeMillis());
	}

	@Override
	public void stopService() {
	}

	@Override
	public void eventOccurred(Event e) {
		if (e instanceof CleanupTimerStartedEvent) {
			CleanupTimerStartedEvent a = (CleanupTimerStartedEvent) e;
			maybeScheduleTask(a.getCleanupDeadline());
		}
	}

	private void maybeScheduleTask(long deadline) {
		synchronized (lock) {
			long minDeadline = Long.MAX_VALUE;
			for (CleanupTask task : pending) {
				if (task.deadline < minDeadline) minDeadline = task.deadline;
			}
			if (deadline < minDeadline) {
				CleanupTask task = new CleanupTask(deadline);
				pending.add(task);
				scheduleTask(task);
			}
		}
	}

	private void scheduleTask(CleanupTask task) {
		long now = clock.currentTimeMillis();
		long delay = max(0, task.deadline - now + BATCH_DELAY_MS);
		if (LOG.isLoggable(INFO)) {
			LOG.info("Scheduling cleanup task in " + delay + " ms");
		}
		taskScheduler.schedule(() -> deleteMessagesAndScheduleNextTask(task),
				dbExecutor, delay, MILLISECONDS);
	}

	private void deleteMessagesAndScheduleNextTask(CleanupTask task) {
		try {
			synchronized (lock) {
				pending.remove(task);
			}
			long deadline = db.transactionWithResult(false, txn -> {
				deleteMessages(txn);
				return db.getNextCleanupDeadline(txn);
			});
			if (deadline != NO_CLEANUP_DEADLINE) {
				maybeScheduleTask(deadline);
			}
		} catch (DbException e) {
			logException(LOG, WARNING, e);
		}
	}

	private void deleteMessages(Transaction txn) throws DbException {
		Map<GroupId, ClientMajorVersion> clientCache = new HashMap<>();
		Map<GroupId, Collection<MessageId>> deleted = new HashMap<>();
		Map<MessageId, GroupId> ids = db.getMessagesToDelete(txn);
		if (LOG.isLoggable(INFO)) LOG.info(ids.size() + " messages to delete");
		for (Entry<MessageId, GroupId> e : ids.entrySet()) {
			MessageId m = e.getKey();
			GroupId g = e.getValue();
			ClientMajorVersion cv = clientCache.get(g);
			if (cv == null) {
				Group group = db.getGroup(txn, g);
				cv = new ClientMajorVersion(group.getClientId(),
						group.getMajorVersion());
				clientCache.put(g, cv);
			}
			CleanupHook hook = hooks.get(cv);
			if (hook == null) {
				if (LOG.isLoggable(WARNING)) {
					LOG.warning("No cleanup hook for " + cv);
				}
			} else if (hook.deleteMessage(txn, g, m)) {
				Collection<MessageId> messageIds = deleted.get(g);
				if (messageIds == null) {
					messageIds = new ArrayList<>();
					deleted.put(g, messageIds);
				}
				messageIds.add(m);
			} else {
				LOG.info("Message was not deleted");
			}
		}
		for (Entry<GroupId, Collection<MessageId>> e : deleted.entrySet()) {
			txn.attach(new MessagesCleanedUpEvent(e.getKey(), e.getValue()));
		}
	}

	private static class CleanupTask {

		private final long deadline;

		private CleanupTask(long deadline) {
			this.deadline = deadline;
		}
	}
}
