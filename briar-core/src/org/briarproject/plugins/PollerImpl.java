package org.briarproject.plugins;

import static java.util.logging.Level.INFO;

import java.util.Collection;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.concurrent.Executor;
import java.util.logging.Logger;

import javax.inject.Inject;

import org.briarproject.api.ContactId;
import org.briarproject.api.plugins.Plugin;
import org.briarproject.api.plugins.PluginExecutor;
import org.briarproject.api.system.Clock;
import org.briarproject.api.transport.ConnectionRegistry;

class PollerImpl implements Poller, Runnable {

	private static final Logger LOG =
			Logger.getLogger(PollerImpl.class.getName());

	private final Executor pluginExecutor;
	private final ConnectionRegistry connRegistry;
	private final Clock clock;
	private final SortedSet<PollTime> pollTimes;

	@Inject
	PollerImpl(@PluginExecutor Executor pluginExecutor,
			ConnectionRegistry connRegistry, Clock clock) {
		this.pluginExecutor = pluginExecutor;
		this.connRegistry = connRegistry;
		this.clock = clock;
		pollTimes = new TreeSet<PollTime>();
	}

	public synchronized void start(Collection<Plugin> plugins) {
		for(Plugin plugin : plugins) schedule(plugin, true);
		new Thread(this, "Poller").start();
	}

	private synchronized void schedule(Plugin plugin, boolean randomise) {
		if(plugin.shouldPoll()) {
			long now = clock.currentTimeMillis();
			long interval = plugin.getPollingInterval();
			// Randomise intervals at startup to spread out connection attempts
			if(randomise) interval = (long) (interval * Math.random());
			pollTimes.add(new PollTime(now + interval, plugin));
		}
	}

	public synchronized void stop() {
		pollTimes.clear();
		notifyAll();
	}

	public void run() {
		while(true) {
			synchronized(this) {
				if(pollTimes.isEmpty()) {
					LOG.info("Finished polling");
					return;
				}
				long now = clock.currentTimeMillis();
				final PollTime p = pollTimes.first();
				if(now >= p.time) {
					boolean removed = pollTimes.remove(p);
					assert removed;
					final Collection<ContactId> connected =
							connRegistry.getConnectedContacts(p.plugin.getId());
					if(LOG.isLoggable(INFO)) {
						String name = p.plugin.getClass().getSimpleName();
						LOG.info("Polling " + name);
					}
					pluginExecutor.execute(new Runnable() {
						public void run() {
							p.plugin.poll(connected);
						}
					});
					schedule(p.plugin, false);
				} else {
					try {
						wait(p.time - now);
					} catch(InterruptedException e) {
						LOG.warning("Interrupted while waiting to poll");
						Thread.currentThread().interrupt();
						return;
					}
				}
			}
		}
	}

	private static class PollTime implements Comparable<PollTime> {

		private final long time;
		private final Plugin plugin;

		private PollTime(long time, Plugin plugin) {
			this.time = time;
			this.plugin = plugin;
		}

		// Must be consistent with equals()
		public int compareTo(PollTime p) {
			if(time < p.time) return -1;
			if(time > p.time) return 1;
			return 0;
		}

		// Must be consistent with equals()
		@Override
		public int hashCode() {
			return (int) (time ^ (time >>> 32)) ^ plugin.hashCode();
		}

		@Override
		public boolean equals(Object o) {
			if(o instanceof PollTime) {
				PollTime p = (PollTime) o;
				return time == p.time && plugin == p.plugin;
			}
			return false;
		}
	}
}
