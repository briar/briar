package org.briarproject.plugins;

import static java.util.logging.Level.INFO;
import static java.util.logging.Level.WARNING;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.logging.Logger;

import javax.inject.Inject;

import org.briarproject.api.ContactId;
import org.briarproject.api.TransportConfig;
import org.briarproject.api.TransportId;
import org.briarproject.api.TransportProperties;
import org.briarproject.api.db.DatabaseComponent;
import org.briarproject.api.db.DbException;
import org.briarproject.api.plugins.Plugin;
import org.briarproject.api.plugins.PluginCallback;
import org.briarproject.api.plugins.PluginExecutor;
import org.briarproject.api.plugins.PluginManager;
import org.briarproject.api.plugins.duplex.DuplexPlugin;
import org.briarproject.api.plugins.duplex.DuplexPluginCallback;
import org.briarproject.api.plugins.duplex.DuplexPluginConfig;
import org.briarproject.api.plugins.duplex.DuplexPluginFactory;
import org.briarproject.api.plugins.duplex.DuplexTransportConnection;
import org.briarproject.api.plugins.simplex.SimplexPlugin;
import org.briarproject.api.plugins.simplex.SimplexPluginCallback;
import org.briarproject.api.plugins.simplex.SimplexPluginConfig;
import org.briarproject.api.plugins.simplex.SimplexPluginFactory;
import org.briarproject.api.plugins.simplex.SimplexTransportReader;
import org.briarproject.api.plugins.simplex.SimplexTransportWriter;
import org.briarproject.api.system.Clock;
import org.briarproject.api.transport.ConnectionDispatcher;
import org.briarproject.api.ui.UiCallback;

// FIXME: Don't make alien calls with a lock held (that includes waiting on a
// latch that depends on an alien call)
class PluginManagerImpl implements PluginManager {

	private static final Logger LOG =
			Logger.getLogger(PluginManagerImpl.class.getName());

	private final Executor pluginExecutor;
	private final SimplexPluginConfig simplexPluginConfig;
	private final DuplexPluginConfig duplexPluginConfig;
	private final Clock clock;
	private final DatabaseComponent db;
	private final Poller poller;
	private final ConnectionDispatcher dispatcher;
	private final UiCallback uiCallback;
	private final Map<TransportId, Plugin> plugins;
	private final List<SimplexPlugin> simplexPlugins;
	private final List<DuplexPlugin> duplexPlugins;

	@Inject
	PluginManagerImpl(@PluginExecutor Executor pluginExecutor,
			SimplexPluginConfig simplexPluginConfig, 
			DuplexPluginConfig duplexPluginConfig, Clock clock,
			DatabaseComponent db, Poller poller,
			ConnectionDispatcher dispatcher, UiCallback uiCallback) {
		this.pluginExecutor = pluginExecutor;
		this.simplexPluginConfig = simplexPluginConfig;
		this.duplexPluginConfig = duplexPluginConfig;
		this.clock = clock;
		this.db = db;
		this.poller = poller;
		this.dispatcher = dispatcher;
		this.uiCallback = uiCallback;
		plugins = new ConcurrentHashMap<TransportId, Plugin>();
		simplexPlugins = new CopyOnWriteArrayList<SimplexPlugin>();
		duplexPlugins = new CopyOnWriteArrayList<DuplexPlugin>();
	}

	public synchronized boolean start() {
		// Instantiate and start the simplex plugins
		LOG.info("Starting simplex plugins");
		Collection<SimplexPluginFactory> sFactories =
				simplexPluginConfig.getFactories();
		final CountDownLatch sLatch = new CountDownLatch(sFactories.size());
		for(SimplexPluginFactory factory : sFactories)
			pluginExecutor.execute(new SimplexPluginStarter(factory, sLatch));
		// Instantiate and start the duplex plugins
		LOG.info("Starting duplex plugins");
		Collection<DuplexPluginFactory> dFactories =
				duplexPluginConfig.getFactories();
		final CountDownLatch dLatch = new CountDownLatch(dFactories.size());
		for(DuplexPluginFactory factory : dFactories)
			pluginExecutor.execute(new DuplexPluginStarter(factory, dLatch));
		// Wait for the plugins to start
		try {
			sLatch.await();
			dLatch.await();
		} catch(InterruptedException e) {
			LOG.warning("Interrupted while starting plugins");
			Thread.currentThread().interrupt();
			return false;
		}
		// Start the poller
		LOG.info("Starting poller");
		List<Plugin> start = new ArrayList<Plugin>(plugins.values());
		poller.start(Collections.unmodifiableList(start));
		return true;
	}

	public synchronized boolean stop() {
		// Stop the poller
		LOG.info("Stopping poller");
		poller.stop();
		final CountDownLatch latch = new CountDownLatch(plugins.size());
		// Stop the simplex plugins
		LOG.info("Stopping simplex plugins");
		for(SimplexPlugin plugin : simplexPlugins)
			pluginExecutor.execute(new PluginStopper(plugin, latch));
		// Stop the duplex plugins
		LOG.info("Stopping duplex plugins");
		for(DuplexPlugin plugin : duplexPlugins)
			pluginExecutor.execute(new PluginStopper(plugin, latch));
		plugins.clear();
		simplexPlugins.clear();
		duplexPlugins.clear();
		// Wait for all the plugins to stop
		try {
			latch.await();
		} catch(InterruptedException e) {
			LOG.warning("Interrupted while stopping plugins");
			Thread.currentThread().interrupt();
			return false;
		}
		return true;
	}

	public Plugin getPlugin(TransportId t) {
		return plugins.get(t);
	}

	public Collection<DuplexPlugin> getInvitationPlugins() {
		List<DuplexPlugin> supported = new ArrayList<DuplexPlugin>();
		for(DuplexPlugin d : duplexPlugins)
			if(d.supportsInvitations()) supported.add(d);
		return Collections.unmodifiableList(supported);
	}

	private class SimplexPluginStarter implements Runnable {

		private final SimplexPluginFactory factory;
		private final CountDownLatch latch;

		private SimplexPluginStarter(SimplexPluginFactory factory,
				CountDownLatch latch) {
			this.factory = factory;
			this.latch = latch;
		}

		public void run() {
			try {
				TransportId id = factory.getId();
				SimplexCallback callback = new SimplexCallback(id);
				SimplexPlugin plugin = factory.createPlugin(callback);
				if(plugin == null) {
					if(LOG.isLoggable(INFO)) {
						String name = factory.getClass().getSimpleName();
						LOG.info(name + " did not create a plugin");
					}
					return;
				}
				try {
					long start = clock.currentTimeMillis();
					db.addTransport(id, plugin.getMaxLatency());
					long duration = clock.currentTimeMillis() - start;
					if(LOG.isLoggable(INFO))
						LOG.info("Adding transport took " + duration + " ms");
				} catch(DbException e) {
					if(LOG.isLoggable(WARNING))
						LOG.log(WARNING, e.toString(), e);
					return;
				}
				try {
					long start = clock.currentTimeMillis();
					boolean started = plugin.start();
					long duration = clock.currentTimeMillis() - start;
					if(started) {
						plugins.put(id, plugin);
						simplexPlugins.add(plugin);
						if(LOG.isLoggable(INFO)) {
							String name = plugin.getClass().getSimpleName();
							LOG.info("Starting " + name + " took " +
									duration + " ms");
						}
					} else {
						if(LOG.isLoggable(WARNING)) {
							String name = plugin.getClass().getSimpleName();
							LOG.warning(name + " did not start");
						}
					}
				} catch(IOException e) {
					if(LOG.isLoggable(WARNING))
						LOG.log(WARNING, e.toString(), e);
				}
			} finally {
				latch.countDown();
			}
		}
	}

	private class DuplexPluginStarter implements Runnable {

		private final DuplexPluginFactory factory;
		private final CountDownLatch latch;

		private DuplexPluginStarter(DuplexPluginFactory factory,
				CountDownLatch latch) {
			this.factory = factory;
			this.latch = latch;
		}

		public void run() {
			try {
				TransportId id = factory.getId();
				DuplexCallback callback = new DuplexCallback(id);
				DuplexPlugin plugin = factory.createPlugin(callback);
				if(plugin == null) {
					if(LOG.isLoggable(INFO)) {
						String name = factory.getClass().getSimpleName();
						LOG.info(name + " did not create a plugin");
					}
					return;
				}
				try {
					long start = clock.currentTimeMillis();
					db.addTransport(id, plugin.getMaxLatency());
					long duration = clock.currentTimeMillis() - start;
					if(LOG.isLoggable(INFO))
						LOG.info("Adding transport took " + duration + " ms");
				} catch(DbException e) {
					if(LOG.isLoggable(WARNING))
						LOG.log(WARNING, e.toString(), e);
					return;
				}
				try {
					long start = clock.currentTimeMillis();
					boolean started = plugin.start();
					long duration = clock.currentTimeMillis() - start;
					if(started) {
						plugins.put(id, plugin);
						duplexPlugins.add(plugin);
						if(LOG.isLoggable(INFO)) {
							String name = plugin.getClass().getSimpleName();
							LOG.info("Starting " + name + " took " +
									duration + " ms");
						}
					} else {
						if(LOG.isLoggable(WARNING)) {
							String name = plugin.getClass().getSimpleName();
							LOG.warning(name + " did not start");
						}
					}
				} catch(IOException e) {
					if(LOG.isLoggable(WARNING))
						LOG.log(WARNING, e.toString(), e);
				}
			} finally {
				latch.countDown();
			}
		}
	}

	private class PluginStopper implements Runnable {

		private final Plugin plugin;
		private final CountDownLatch latch;

		private PluginStopper(Plugin plugin, CountDownLatch latch) {
			this.plugin = plugin;
			this.latch = latch;
		}

		public void run() {
			try {
				long start = clock.currentTimeMillis();
				plugin.stop();
				long duration = clock.currentTimeMillis() - start;
				if(LOG.isLoggable(INFO)) {
					String name = plugin.getClass().getSimpleName();
					LOG.info("Stopping " + name + " took " + duration + " ms");
				}
			} catch(IOException e) {
				if(LOG.isLoggable(WARNING)) LOG.log(WARNING, e.toString(), e);
			} finally {
				latch.countDown();
			}
		}
	}

	private abstract class PluginCallbackImpl implements PluginCallback {

		protected final TransportId id;

		protected PluginCallbackImpl(TransportId id) {
			this.id = id;
		}

		public TransportConfig getConfig() {
			try {
				return db.getConfig(id);
			} catch(DbException e) {
				if(LOG.isLoggable(WARNING)) LOG.log(WARNING, e.toString(), e);
				return new TransportConfig();
			}
		}

		public TransportProperties getLocalProperties() {
			try {
				TransportProperties p = db.getLocalProperties(id);
				return p == null ? new TransportProperties() : p;
			} catch(DbException e) {
				if(LOG.isLoggable(WARNING)) LOG.log(WARNING, e.toString(), e);
				return new TransportProperties();
			}
		}

		public Map<ContactId, TransportProperties> getRemoteProperties() {
			try {
				return db.getRemoteProperties(id);
			} catch(DbException e) {
				if(LOG.isLoggable(WARNING)) LOG.log(WARNING, e.toString(), e);
				return Collections.emptyMap();
			}
		}

		public void mergeConfig(TransportConfig c) {
			try {
				db.mergeConfig(id, c);
			} catch(DbException e) {
				if(LOG.isLoggable(WARNING)) LOG.log(WARNING, e.toString(), e);
			}
		}

		public void mergeLocalProperties(TransportProperties p) {
			try {
				db.mergeLocalProperties(id, p);
			} catch(DbException e) {
				if(LOG.isLoggable(WARNING)) LOG.log(WARNING, e.toString(), e);
			}
		}

		public int showChoice(String[] options, String... message) {
			return uiCallback.showChoice(options, message);
		}

		public boolean showConfirmationMessage(String... message) {
			return uiCallback.showConfirmationMessage(message);
		}

		public void showMessage(String... message) {
			uiCallback.showMessage(message);
		}
	}

	private class SimplexCallback extends PluginCallbackImpl
	implements SimplexPluginCallback {

		private SimplexCallback(TransportId id) {
			super(id);
		}

		public void readerCreated(SimplexTransportReader r) {
			dispatcher.dispatchReader(id, r);
		}

		public void writerCreated(ContactId c, SimplexTransportWriter w) {
			dispatcher.dispatchWriter(c, id, w);
		}
	}

	private class DuplexCallback extends PluginCallbackImpl
	implements DuplexPluginCallback {

		private DuplexCallback(TransportId id) {
			super(id);
		}

		public void incomingConnectionCreated(DuplexTransportConnection d) {
			dispatcher.dispatchIncomingConnection(id, d);
		}

		public void outgoingConnectionCreated(ContactId c,
				DuplexTransportConnection d) {
			dispatcher.dispatchOutgoingConnection(c, id, d);
		}
	}
}