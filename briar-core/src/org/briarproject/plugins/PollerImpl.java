package org.briarproject.plugins;

import org.briarproject.api.TransportId;
import org.briarproject.api.lifecycle.IoExecutor;
import org.briarproject.api.plugins.ConnectionRegistry;
import org.briarproject.api.plugins.Plugin;

import java.security.SecureRandom;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.ScheduledExecutorService;
import java.util.logging.Logger;

import javax.inject.Inject;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.logging.Level.INFO;

class PollerImpl implements Poller {

	private static final Logger LOG =
			Logger.getLogger(PollerImpl.class.getName());

	private final Executor ioExecutor;
	private final ScheduledExecutorService scheduler;
	private final ConnectionRegistry connectionRegistry;
	private final SecureRandom random;
	private final Map<TransportId, PollTask> tasks;

	@Inject
	PollerImpl(@IoExecutor Executor ioExecutor,
			ScheduledExecutorService scheduler,
			ConnectionRegistry connectionRegistry, SecureRandom random) {
		this.ioExecutor = ioExecutor;
		this.connectionRegistry = connectionRegistry;
		this.random = random;
		this.scheduler = scheduler;
		tasks = new ConcurrentHashMap<TransportId, PollTask>();
	}

	@Override
	public void pollNow(Plugin p) {
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
