package org.briarproject.plugins;

import org.briarproject.api.TransportId;
import org.briarproject.api.contact.ContactId;
import org.briarproject.api.event.ConnectionClosedEvent;
import org.briarproject.api.event.ContactStatusChangedEvent;
import org.briarproject.api.event.Event;
import org.briarproject.api.event.EventListener;
import org.briarproject.api.event.TransportEnabledEvent;
import org.briarproject.api.lifecycle.IoExecutor;
import org.briarproject.api.plugins.ConnectionManager;
import org.briarproject.api.plugins.ConnectionRegistry;
import org.briarproject.api.plugins.Plugin;
import org.briarproject.api.plugins.PluginManager;
import org.briarproject.api.plugins.TransportConnectionWriter;
import org.briarproject.api.plugins.duplex.DuplexPlugin;
import org.briarproject.api.plugins.duplex.DuplexTransportConnection;
import org.briarproject.api.plugins.simplex.SimplexPlugin;

import java.security.SecureRandom;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.ScheduledExecutorService;
import java.util.logging.Logger;

import javax.inject.Inject;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.logging.Level.INFO;

class PollerImpl implements Poller, EventListener {

	private static final Logger LOG =
			Logger.getLogger(PollerImpl.class.getName());

	private final Executor ioExecutor;
	private final ScheduledExecutorService scheduler;
	private final ConnectionManager connectionManager;
	private final ConnectionRegistry connectionRegistry;
	private final PluginManager pluginManager;
	private final SecureRandom random;
	private final Map<TransportId, PollTask> tasks;

	@Inject
	PollerImpl(@IoExecutor Executor ioExecutor,
			ScheduledExecutorService scheduler,
			ConnectionManager connectionManager,
			ConnectionRegistry connectionRegistry, PluginManager pluginManager,
			SecureRandom random) {
		this.ioExecutor = ioExecutor;
		this.connectionManager = connectionManager;
		this.connectionRegistry = connectionRegistry;
		this.pluginManager = pluginManager;
		this.random = random;
		this.scheduler = scheduler;
		tasks = new ConcurrentHashMap<TransportId, PollTask>();
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
			if (!c.isIncoming()) {
				// Connect to the disconnected contact
				connectToContact(c.getContactId(), c.getTransportId());
			}
		} else if (e instanceof TransportEnabledEvent) {
			TransportEnabledEvent t = (TransportEnabledEvent) e;
			Plugin p = pluginManager.getPlugin(t.getTransportId());
			if (p.shouldPoll()) pollNow(p);
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

	private void pollNow(Plugin p) {
		// Randomise next polling interval
		schedule(p, 0, true);
	}

	private void schedule(Plugin p, int interval, boolean randomiseNext) {
		// Replace any previously scheduled task for this plugin
		PollTask task = new PollTask(p, randomiseNext);
		PollTask replaced = tasks.put(p.getId(), task);
		if (replaced != null) replaced.cancel();
		scheduler.schedule(task, interval, MILLISECONDS);
	}

	private void poll(final Plugin p) {
		ioExecutor.execute(new Runnable() {
			@Override
			public void run() {
				if (LOG.isLoggable(INFO))
					LOG.info("Polling " + p.getClass().getSimpleName());
				p.poll(connectionRegistry.getConnectedContacts(p.getId()));
			}
		});
	}

	private class PollTask implements Runnable {

		private final Plugin plugin;
		private final boolean randomiseNext;

		private volatile boolean cancelled = false;

		private PollTask(Plugin plugin, boolean randomiseNext) {
			this.plugin = plugin;
			this.randomiseNext = randomiseNext;
		}

		private void cancel() {
			cancelled = true;
		}

		@Override
		public void run() {
			if (cancelled) return;
			tasks.remove(plugin.getId());
			int interval = plugin.getPollingInterval();
			if (randomiseNext)
				interval = (int) (interval * random.nextDouble());
			schedule(plugin, interval, false);
			poll(plugin);
		}
	}
}
