package org.briarproject.plugins;

import org.briarproject.api.lifecycle.IoExecutor;
import org.briarproject.api.plugins.ConnectionRegistry;
import org.briarproject.api.plugins.Plugin;
import org.briarproject.api.system.Timer;

import java.util.TimerTask;
import java.util.concurrent.Executor;
import java.util.logging.Logger;

import javax.inject.Inject;

import static java.util.logging.Level.INFO;

class PollerImpl implements Poller {

	private static final Logger LOG =
			Logger.getLogger(PollerImpl.class.getName());

	private final Executor ioExecutor;
	private final ConnectionRegistry connectionRegistry;
	private final Timer timer;

	@Inject
	PollerImpl(@IoExecutor Executor ioExecutor,
			ConnectionRegistry connectionRegistry, Timer timer) {
		this.ioExecutor = ioExecutor;
		this.connectionRegistry = connectionRegistry;
		this.timer = timer;
	}

	public void stop() {
		timer.cancel();
	}

	public void addPlugin(Plugin p) {
		schedule(p, true);
	}

	private void schedule(Plugin plugin, boolean randomise) {
		long interval = plugin.getPollingInterval();
		// Randomise intervals at startup to spread out connection attempts
		if (randomise) interval = (long) (interval * Math.random());
		timer.schedule(new PollTask(plugin), interval);
	}

	public void pollNow(final Plugin p) {
		ioExecutor.execute(new Runnable() {
			public void run() {
				if (LOG.isLoggable(INFO))
					LOG.info("Polling " + p.getClass().getSimpleName());
				p.poll(connectionRegistry.getConnectedContacts(p.getId()));
			}
		});
	}

	private class PollTask extends TimerTask {

		private final Plugin plugin;

		private PollTask(Plugin plugin) {
			this.plugin = plugin;
		}

		@Override
		public void run() {
			pollNow(plugin);
			schedule(plugin, false);
		}
	}
}
