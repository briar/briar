package org.briarproject.plugins;

import org.briarproject.api.TransportId;
import org.briarproject.api.lifecycle.IoExecutor;
import org.briarproject.api.plugins.ConnectionRegistry;
import org.briarproject.api.plugins.Plugin;
import org.briarproject.api.system.Timer;

import java.security.SecureRandom;
import java.util.Map;
import java.util.TimerTask;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.logging.Logger;

import javax.inject.Inject;

import static java.util.logging.Level.INFO;

class PollerImpl implements Poller {

	private static final Logger LOG =
			Logger.getLogger(PollerImpl.class.getName());

	private final Executor ioExecutor;
	private final ConnectionRegistry connectionRegistry;
	private final SecureRandom random;
	private final Timer timer;
	private final Map<TransportId, PollTask> tasks;

	@Inject
	PollerImpl(@IoExecutor Executor ioExecutor,
			ConnectionRegistry connectionRegistry, SecureRandom random,
			Timer timer) {
		this.ioExecutor = ioExecutor;
		this.connectionRegistry = connectionRegistry;
		this.random = random;
		this.timer = timer;
		tasks = new ConcurrentHashMap<TransportId, PollTask>();
	}

	@Override
	public void stop() {
		timer.cancel();
	}

	@Override
	public void pollNow(Plugin p) {
		// Randomise next polling interval
		if (p.shouldPoll()) schedule(p, 0, true);
	}

	private void schedule(Plugin p, int interval, boolean randomiseNext) {
		// Replace any previously scheduled task for this plugin
		PollTask task = new PollTask(p, randomiseNext);
		PollTask replaced = tasks.put(p.getId(), task);
		if (replaced != null) replaced.cancel();
		timer.schedule(task, interval);
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

	private class PollTask extends TimerTask {

		private final Plugin plugin;
		private final boolean randomiseNext;

		private PollTask(Plugin plugin, boolean randomiseNext) {
			this.plugin = plugin;
			this.randomiseNext = randomiseNext;
		}

		@Override
		public void run() {
			tasks.remove(plugin.getId());
			int interval = plugin.getPollingInterval();
			if (randomiseNext)
				interval = (int) (interval * random.nextDouble());
			schedule(plugin, interval, false);
			poll(plugin);
		}
	}
}
