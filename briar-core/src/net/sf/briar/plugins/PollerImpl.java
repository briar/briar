package net.sf.briar.plugins;

import static java.util.logging.Level.INFO;

import java.util.Collection;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.concurrent.ExecutorService;
import java.util.logging.Logger;

import net.sf.briar.api.ContactId;
import net.sf.briar.api.clock.Clock;
import net.sf.briar.api.plugins.Plugin;
import net.sf.briar.api.plugins.PluginExecutor;
import net.sf.briar.api.transport.ConnectionRegistry;

import com.google.inject.Inject;

class PollerImpl implements Poller, Runnable {

	private static final Logger LOG =
			Logger.getLogger(PollerImpl.class.getName());

	private final ExecutorService pluginExecutor;
	private final ConnectionRegistry connRegistry;
	private final Clock clock;
	private final SortedSet<PollTime> pollTimes;

	@Inject
	PollerImpl(@PluginExecutor ExecutorService pluginExecutor,
			ConnectionRegistry connRegistry, Clock clock) {
		this.pluginExecutor = pluginExecutor;
		this.connRegistry = connRegistry;
		this.clock = clock;
		pollTimes = new TreeSet<PollTime>();
	}

	public synchronized void start(Collection<Plugin> plugins) {
		for(Plugin plugin : plugins) schedule(plugin);
		new Thread(this, "Poller").start();
	}

	private synchronized void schedule(Plugin plugin) {
		if(plugin.shouldPoll()) {
			long now = clock.currentTimeMillis();
			long interval = plugin.getPollingInterval();
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
					if(LOG.isLoggable(INFO)) LOG.info("Finished polling");
					return;
				}
				long now = clock.currentTimeMillis();
				final PollTime p = pollTimes.first();
				if(now >= p.time) {
					boolean removed = pollTimes.remove(p);
					assert removed;
					final Collection<ContactId> connected =
							connRegistry.getConnectedContacts(p.plugin.getId());
					if(LOG.isLoggable(INFO))
						LOG.info("Polling " + p.plugin.getClass().getName());
					pluginExecutor.submit(new Runnable() {
						public void run() {
							p.plugin.poll(connected);
						}
					});
					schedule(p.plugin);
				} else {
					try {
						wait(p.time - now);
					} catch(InterruptedException e) {
						if(LOG.isLoggable(INFO))
							LOG.info("Interrupted while waiting to poll");
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
