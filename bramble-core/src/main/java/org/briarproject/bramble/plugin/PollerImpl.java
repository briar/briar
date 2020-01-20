package org.briarproject.bramble.plugin;

import org.briarproject.bramble.api.Pair;
import org.briarproject.bramble.api.contact.ContactId;
import org.briarproject.bramble.api.contact.event.ContactAddedEvent;
import org.briarproject.bramble.api.db.DbException;
import org.briarproject.bramble.api.event.Event;
import org.briarproject.bramble.api.event.EventListener;
import org.briarproject.bramble.api.lifecycle.IoExecutor;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.plugin.ConnectionHandler;
import org.briarproject.bramble.api.plugin.ConnectionManager;
import org.briarproject.bramble.api.plugin.ConnectionRegistry;
import org.briarproject.bramble.api.plugin.Plugin;
import org.briarproject.bramble.api.plugin.PluginManager;
import org.briarproject.bramble.api.plugin.TransportConnectionReader;
import org.briarproject.bramble.api.plugin.TransportConnectionWriter;
import org.briarproject.bramble.api.plugin.TransportId;
import org.briarproject.bramble.api.plugin.duplex.DuplexPlugin;
import org.briarproject.bramble.api.plugin.duplex.DuplexTransportConnection;
import org.briarproject.bramble.api.plugin.event.ConnectionClosedEvent;
import org.briarproject.bramble.api.plugin.event.ConnectionOpenedEvent;
import org.briarproject.bramble.api.plugin.event.TransportActiveEvent;
import org.briarproject.bramble.api.plugin.event.TransportInactiveEvent;
import org.briarproject.bramble.api.plugin.simplex.SimplexPlugin;
import org.briarproject.bramble.api.properties.TransportProperties;
import org.briarproject.bramble.api.properties.TransportPropertyManager;
import org.briarproject.bramble.api.system.Clock;
import org.briarproject.bramble.api.system.Scheduler;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.Executor;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Logger;

import javax.annotation.concurrent.GuardedBy;
import javax.annotation.concurrent.ThreadSafe;
import javax.inject.Inject;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.logging.Level.INFO;
import static java.util.logging.Level.WARNING;
import static java.util.logging.Logger.getLogger;
import static org.briarproject.bramble.util.LogUtils.logException;

@ThreadSafe
@NotNullByDefault
class PollerImpl implements Poller, EventListener {

	private static final Logger LOG = getLogger(PollerImpl.class.getName());

	private final Executor ioExecutor;
	private final ScheduledExecutorService scheduler;
	private final ConnectionManager connectionManager;
	private final ConnectionRegistry connectionRegistry;
	private final PluginManager pluginManager;
	private final TransportPropertyManager transportPropertyManager;
	private final SecureRandom random;
	private final Clock clock;
	private final Lock lock;
	@GuardedBy("lock")
	private final Map<TransportId, ScheduledPollTask> tasks;

	@Inject
	PollerImpl(@IoExecutor Executor ioExecutor,
			@Scheduler ScheduledExecutorService scheduler,
			ConnectionManager connectionManager,
			ConnectionRegistry connectionRegistry, PluginManager pluginManager,
			TransportPropertyManager transportPropertyManager,
			SecureRandom random, Clock clock) {
		this.ioExecutor = ioExecutor;
		this.scheduler = scheduler;
		this.connectionManager = connectionManager;
		this.connectionRegistry = connectionRegistry;
		this.pluginManager = pluginManager;
		this.transportPropertyManager = transportPropertyManager;
		this.random = random;
		this.clock = clock;
		lock = new ReentrantLock();
		tasks = new HashMap<>();
	}

	@Override
	public void eventOccurred(Event e) {
		if (e instanceof ContactAddedEvent) {
			ContactAddedEvent c = (ContactAddedEvent) e;
			// Connect to the newly added contact
			connectToContact(c.getContactId());
		} else if (e instanceof ConnectionClosedEvent) {
			ConnectionClosedEvent c = (ConnectionClosedEvent) e;
			// Reschedule polling, the polling interval may have decreased
			reschedule(c.getTransportId());
			if (!c.isIncoming()) {
				// Connect to the disconnected contact
				connectToContact(c.getContactId(), c.getTransportId());
			}
		} else if (e instanceof ConnectionOpenedEvent) {
			ConnectionOpenedEvent c = (ConnectionOpenedEvent) e;
			// Reschedule polling, the polling interval may have decreased
			reschedule(c.getTransportId());
		} else if (e instanceof TransportActiveEvent) {
			TransportActiveEvent t = (TransportActiveEvent) e;
			// Poll the newly activated transport
			pollNow(t.getTransportId());
		} else if (e instanceof TransportInactiveEvent) {
			TransportInactiveEvent t = (TransportInactiveEvent) e;
			// Cancel polling for the deactivated transport
			cancel(t.getTransportId());
		}
	}

	private void connectToContact(ContactId c) {
		for (SimplexPlugin s : pluginManager.getSimplexPlugins())
			if (s.shouldPoll()) connectToContact(c, s);
		for (DuplexPlugin d : pluginManager.getDuplexPlugins())
			if (d.shouldPoll()) connectToContact(c, d);
	}

	private void connectToContact(ContactId c, TransportId t) {
		Plugin p = pluginManager.getPlugin(t);
		if (p instanceof SimplexPlugin && p.shouldPoll())
			connectToContact(c, (SimplexPlugin) p);
		else if (p instanceof DuplexPlugin && p.shouldPoll())
			connectToContact(c, (DuplexPlugin) p);
	}

	private void connectToContact(ContactId c, SimplexPlugin p) {
		ioExecutor.execute(() -> {
			TransportId t = p.getId();
			if (connectionRegistry.isConnected(c, t)) return;
			try {
				TransportProperties props =
						transportPropertyManager.getRemoteProperties(c, t);
				TransportConnectionWriter w = p.createWriter(props);
				if (w != null)
					connectionManager.manageOutgoingConnection(c, t, w);
			} catch (DbException e) {
				logException(LOG, WARNING, e);
			}
		});
	}

	private void connectToContact(ContactId c, DuplexPlugin p) {
		ioExecutor.execute(() -> {
			TransportId t = p.getId();
			if (connectionRegistry.isConnected(c, t)) return;
			try {
				TransportProperties props =
						transportPropertyManager.getRemoteProperties(c, t);
				DuplexTransportConnection d = p.createConnection(props);
				if (d != null)
					connectionManager.manageOutgoingConnection(c, t, d);
			} catch (DbException e) {
				logException(LOG, WARNING, e);
			}
		});
	}

	private void reschedule(TransportId t) {
		Plugin p = pluginManager.getPlugin(t);
		if (p != null && p.shouldPoll())
			schedule(p, p.getPollingInterval(), false);
	}

	private void pollNow(TransportId t) {
		Plugin p = pluginManager.getPlugin(t);
		// Randomise next polling interval
		if (p != null && p.shouldPoll()) schedule(p, 0, true);
	}

	private void schedule(Plugin p, int delay, boolean randomiseNext) {
		// Replace any later scheduled task for this plugin
		long due = clock.currentTimeMillis() + delay;
		TransportId t = p.getId();
		lock.lock();
		try {
			ScheduledPollTask scheduled = tasks.get(t);
			if (scheduled == null || due < scheduled.task.due) {
				// If a later task exists, cancel it. If it's already started
				// it will abort safely when it finds it's been replaced
				if (scheduled != null) scheduled.future.cancel(false);
				PollTask task = new PollTask(p, due, randomiseNext);
				Future future = scheduler.schedule(() ->
						ioExecutor.execute(task), delay, MILLISECONDS);
				tasks.put(t, new ScheduledPollTask(task, future));
			}
		} finally {
			lock.unlock();
		}
	}

	private void cancel(TransportId t) {
		lock.lock();
		try {
			ScheduledPollTask scheduled = tasks.remove(t);
			if (scheduled != null) scheduled.future.cancel(false);
		} finally {
			lock.unlock();
		}
	}

	@IoExecutor
	private void poll(Plugin p) {
		TransportId t = p.getId();
		if (LOG.isLoggable(INFO)) LOG.info("Polling plugin " + t);
		try {
			Map<ContactId, TransportProperties> remote =
					transportPropertyManager.getRemoteProperties(t);
			Collection<ContactId> connected =
					connectionRegistry.getConnectedContacts(t);
			Collection<Pair<TransportProperties, ConnectionHandler>>
					properties = new ArrayList<>();
			for (Entry<ContactId, TransportProperties> e : remote.entrySet()) {
				ContactId c = e.getKey();
				if (!connected.contains(c))
					properties.add(new Pair<>(e.getValue(), new Handler(c, t)));
			}
			if (!properties.isEmpty()) p.poll(properties);
		} catch (DbException e) {
			logException(LOG, WARNING, e);
		}
	}

	private class ScheduledPollTask {

		private final PollTask task;
		private final Future future;

		private ScheduledPollTask(PollTask task, Future future) {
			this.task = task;
			this.future = future;
		}
	}

	private class PollTask implements Runnable {

		private final Plugin plugin;
		private final long due;
		private final boolean randomiseNext;

		private PollTask(Plugin plugin, long due, boolean randomiseNext) {
			this.plugin = plugin;
			this.due = due;
			this.randomiseNext = randomiseNext;
		}

		@Override
		@IoExecutor
		public void run() {
			lock.lock();
			try {
				TransportId t = plugin.getId();
				ScheduledPollTask scheduled = tasks.get(t);
				if (scheduled != null && scheduled.task != this)
					return; // Replaced by another task
				tasks.remove(t);
			} finally {
				lock.unlock();
			}
			int delay = plugin.getPollingInterval();
			if (randomiseNext) delay = (int) (delay * random.nextDouble());
			schedule(plugin, delay, false);
			poll(plugin);
		}
	}

	private class Handler implements ConnectionHandler {

		private final ContactId contactId;
		private final TransportId transportId;

		private Handler(ContactId contactId, TransportId transportId) {
			this.contactId = contactId;
			this.transportId = transportId;
		}

		@Override
		public void handleConnection(DuplexTransportConnection c) {
			connectionManager.manageOutgoingConnection(contactId,
					transportId, c);
		}

		@Override
		public void handleReader(TransportConnectionReader r) {
			// TODO: Support simplex plugins that read from outgoing connections
			throw new UnsupportedOperationException();
		}

		@Override
		public void handleWriter(TransportConnectionWriter w) {
			connectionManager.manageOutgoingConnection(contactId,
					transportId, w);
		}
	}
}
