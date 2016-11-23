package org.briarproject.bramble.plugin;

import org.briarproject.bramble.api.contact.ContactId;
import org.briarproject.bramble.api.contact.event.ContactStatusChangedEvent;
import org.briarproject.bramble.api.event.Event;
import org.briarproject.bramble.api.event.EventListener;
import org.briarproject.bramble.api.lifecycle.IoExecutor;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.plugin.ConnectionManager;
import org.briarproject.bramble.api.plugin.ConnectionRegistry;
import org.briarproject.bramble.api.plugin.Plugin;
import org.briarproject.bramble.api.plugin.PluginManager;
import org.briarproject.bramble.api.plugin.TransportConnectionWriter;
import org.briarproject.bramble.api.plugin.TransportId;
import org.briarproject.bramble.api.plugin.duplex.DuplexPlugin;
import org.briarproject.bramble.api.plugin.duplex.DuplexTransportConnection;
import org.briarproject.bramble.api.plugin.event.ConnectionClosedEvent;
import org.briarproject.bramble.api.plugin.event.ConnectionOpenedEvent;
import org.briarproject.bramble.api.plugin.event.TransportEnabledEvent;
import org.briarproject.bramble.api.plugin.simplex.SimplexPlugin;
import org.briarproject.bramble.api.system.Clock;
import org.briarproject.bramble.api.system.Scheduler;

import java.security.SecureRandom;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Logger;

import javax.annotation.concurrent.ThreadSafe;
import javax.inject.Inject;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.logging.Level.INFO;

@ThreadSafe
@NotNullByDefault
class Poller implements EventListener {

	private static final Logger LOG = Logger.getLogger(Poller.class.getName());

	private final Executor ioExecutor;
	private final ScheduledExecutorService scheduler;
	private final ConnectionManager connectionManager;
	private final ConnectionRegistry connectionRegistry;
	private final PluginManager pluginManager;
	private final SecureRandom random;
	private final Clock clock;
	private final Lock lock;
	private final Map<TransportId, PollTask> tasks; // Locking: lock

	@Inject
	Poller(@IoExecutor Executor ioExecutor,
			@Scheduler ScheduledExecutorService scheduler,
			ConnectionManager connectionManager,
			ConnectionRegistry connectionRegistry, PluginManager pluginManager,
			SecureRandom random, Clock clock) {
		this.ioExecutor = ioExecutor;
		this.scheduler = scheduler;
		this.connectionManager = connectionManager;
		this.connectionRegistry = connectionRegistry;
		this.pluginManager = pluginManager;
		this.random = random;
		this.clock = clock;
		lock = new ReentrantLock();
		tasks = new HashMap<TransportId, PollTask>();
	}

	@Override
	public void eventOccurred(Event e) {
		if (e instanceof ContactStatusChangedEvent) {
			ContactStatusChangedEvent c = (ContactStatusChangedEvent) e;
			if (c.isActive()) {
				// Connect to the newly activated contact
				connectToContact(c.getContactId());
			}
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
		} else if (e instanceof TransportEnabledEvent) {
			TransportEnabledEvent t = (TransportEnabledEvent) e;
			// Poll the newly enabled transport
			pollNow(t.getTransportId());
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

	private void connectToContact(final ContactId c, final SimplexPlugin p) {
		ioExecutor.execute(new Runnable() {
			@Override
			public void run() {
				TransportId t = p.getId();
				if (!connectionRegistry.isConnected(c, t)) {
					TransportConnectionWriter w = p.createWriter(c);
					if (w != null)
						connectionManager.manageOutgoingConnection(c, t, w);
				}
			}
		});
	}

	private void connectToContact(final ContactId c, final DuplexPlugin p) {
		ioExecutor.execute(new Runnable() {
			@Override
			public void run() {
				TransportId t = p.getId();
				if (!connectionRegistry.isConnected(c, t)) {
					DuplexTransportConnection d = p.createConnection(c);
					if (d != null)
						connectionManager.manageOutgoingConnection(c, t, d);
				}
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
			PollTask scheduled = tasks.get(t);
			if (scheduled == null || due < scheduled.due) {
				final PollTask task = new PollTask(p, due, randomiseNext);
				tasks.put(t, task);
				scheduler.schedule(new Runnable() {
					@Override
					public void run() {
						ioExecutor.execute(task);
					}
				}, delay, MILLISECONDS);
			}
		} finally {
			lock.unlock();
		}
	}

	@IoExecutor
	private void poll(final Plugin p) {
		TransportId t = p.getId();
		if (LOG.isLoggable(INFO)) LOG.info("Polling plugin " + t);
		p.poll(connectionRegistry.getConnectedContacts(t));
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
				if (tasks.get(t) != this) return; // Replaced by another task
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
}
