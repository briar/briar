package net.sf.briar.plugins;

import java.util.Collection;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.logging.Level;
import java.util.logging.Logger;

import net.sf.briar.api.ContactId;
import net.sf.briar.api.plugins.Plugin;
import net.sf.briar.api.transport.ConnectionRegistry;

import com.google.inject.Inject;

class PollerImpl implements Poller, Runnable {

	private static final Logger LOG =
		Logger.getLogger(PollerImpl.class.getName());

	private final ConnectionRegistry connRegistry;
	private final SortedSet<PollTime> pollTimes;

	@Inject
	PollerImpl(ConnectionRegistry connRegistry) {
		this.connRegistry = connRegistry;
		pollTimes = new TreeSet<PollTime>();
	}

	public synchronized void startPolling(Collection<Plugin> plugins) {
		for(Plugin plugin : plugins) schedule(plugin);
		new Thread(this).start();
	}

	private synchronized void schedule(Plugin plugin) {
		if(plugin.shouldPoll()) {
			long now = System.currentTimeMillis();
			long interval = plugin.getPollingInterval();
			pollTimes.add(new PollTime(now + interval, plugin));
		}
	}

	public synchronized void stopPolling() {
		pollTimes.clear();
		notifyAll();
	}

	public void run() {
		while(true) {
			synchronized(this) {
				if(pollTimes.isEmpty()) return;
				PollTime p = pollTimes.first();
				long now = System.currentTimeMillis();
				if(now <= p.time) {
					pollTimes.remove(p);
					Collection<ContactId> connected =
						connRegistry.getConnectedContacts(p.plugin.getId());
					try {
						p.plugin.poll(connected);
					} catch(RuntimeException e) {
						if(LOG.isLoggable(Level.WARNING))
							LOG.warning("Plugin " + p.plugin.getId() + " " + e);
					}
					schedule(p.plugin);
				} else {
					try {
						wait(p.time - now);
					} catch(InterruptedException e) {
						if(LOG.isLoggable(Level.INFO))
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

		public int compareTo(PollTime p) {
			if(time < p.time) return -1;
			if(time > p.time) return 1;
			return 0;
		}
	}
}
