package org.briarproject.plugins;

import org.briarproject.api.TransportId;
import org.briarproject.api.contact.ContactId;
import org.briarproject.api.db.DatabaseComponent;
import org.briarproject.api.db.DbException;
import org.briarproject.api.db.Transaction;
import org.briarproject.api.event.EventBus;
import org.briarproject.api.event.TransportDisabledEvent;
import org.briarproject.api.event.TransportEnabledEvent;
import org.briarproject.api.lifecycle.IoExecutor;
import org.briarproject.api.lifecycle.Service;
import org.briarproject.api.plugins.ConnectionManager;
import org.briarproject.api.plugins.Plugin;
import org.briarproject.api.plugins.PluginCallback;
import org.briarproject.api.plugins.PluginManager;
import org.briarproject.api.plugins.TransportConnectionReader;
import org.briarproject.api.plugins.TransportConnectionWriter;
import org.briarproject.api.plugins.duplex.DuplexPlugin;
import org.briarproject.api.plugins.duplex.DuplexPluginCallback;
import org.briarproject.api.plugins.duplex.DuplexPluginConfig;
import org.briarproject.api.plugins.duplex.DuplexPluginFactory;
import org.briarproject.api.plugins.duplex.DuplexTransportConnection;
import org.briarproject.api.plugins.simplex.SimplexPlugin;
import org.briarproject.api.plugins.simplex.SimplexPluginCallback;
import org.briarproject.api.plugins.simplex.SimplexPluginConfig;
import org.briarproject.api.plugins.simplex.SimplexPluginFactory;
import org.briarproject.api.properties.TransportProperties;
import org.briarproject.api.properties.TransportPropertyManager;
import org.briarproject.api.settings.Settings;
import org.briarproject.api.settings.SettingsManager;
import org.briarproject.api.system.Clock;
import org.briarproject.api.ui.UiCallback;

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

import static java.util.logging.Level.INFO;
import static java.util.logging.Level.WARNING;

class PluginManagerImpl implements PluginManager, Service {

	private static final Logger LOG =
			Logger.getLogger(PluginManagerImpl.class.getName());

	private final Executor ioExecutor;
	private final EventBus eventBus;
	private final SimplexPluginConfig simplexPluginConfig;
	private final DuplexPluginConfig duplexPluginConfig;
	private final Clock clock;
	private final DatabaseComponent db;
	private final Poller poller;
	private final ConnectionManager connectionManager;
	private final SettingsManager settingsManager;
	private final TransportPropertyManager transportPropertyManager;
	private final UiCallback uiCallback;
	private final Map<TransportId, Plugin> plugins;
	private final List<SimplexPlugin> simplexPlugins;
	private final List<DuplexPlugin> duplexPlugins;

	@Inject
	PluginManagerImpl(@IoExecutor Executor ioExecutor, EventBus eventBus,
			SimplexPluginConfig simplexPluginConfig,
			DuplexPluginConfig duplexPluginConfig, Clock clock,
			DatabaseComponent db, Poller poller,
			ConnectionManager connectionManager,
			SettingsManager settingsManager,
			TransportPropertyManager transportPropertyManager,
			UiCallback uiCallback) {
		this.ioExecutor = ioExecutor;
		this.eventBus = eventBus;
		this.simplexPluginConfig = simplexPluginConfig;
		this.duplexPluginConfig = duplexPluginConfig;
		this.clock = clock;
		this.db = db;
		this.poller = poller;
		this.connectionManager = connectionManager;
		this.settingsManager = settingsManager;
		this.transportPropertyManager = transportPropertyManager;
		this.uiCallback = uiCallback;
		plugins = new ConcurrentHashMap<TransportId, Plugin>();
		simplexPlugins = new CopyOnWriteArrayList<SimplexPlugin>();
		duplexPlugins = new CopyOnWriteArrayList<DuplexPlugin>();
	}

	@Override
	public boolean start() {
		// Instantiate and start the simplex plugins
		LOG.info("Starting simplex plugins");
		Collection<SimplexPluginFactory> sFactories =
				simplexPluginConfig.getFactories();
		final CountDownLatch sLatch = new CountDownLatch(sFactories.size());
		for (SimplexPluginFactory factory : sFactories)
			ioExecutor.execute(new SimplexPluginStarter(factory, sLatch));
		// Instantiate and start the duplex plugins
		LOG.info("Starting duplex plugins");
		Collection<DuplexPluginFactory> dFactories =
				duplexPluginConfig.getFactories();
		final CountDownLatch dLatch = new CountDownLatch(dFactories.size());
		for (DuplexPluginFactory factory : dFactories)
			ioExecutor.execute(new DuplexPluginStarter(factory, dLatch));
		// Wait for the plugins to start
		try {
			sLatch.await();
			dLatch.await();
		} catch (InterruptedException e) {
			LOG.warning("Interrupted while starting plugins");
			Thread.currentThread().interrupt();
			return false;
		}
		return true;
	}

	@Override
	public boolean stop() {
		// Stop the poller
		LOG.info("Stopping poller");
		poller.stop();
		final CountDownLatch latch = new CountDownLatch(plugins.size());
		// Stop the simplex plugins
		LOG.info("Stopping simplex plugins");
		for (SimplexPlugin plugin : simplexPlugins)
			ioExecutor.execute(new PluginStopper(plugin, latch));
		// Stop the duplex plugins
		LOG.info("Stopping duplex plugins");
		for (DuplexPlugin plugin : duplexPlugins)
			ioExecutor.execute(new PluginStopper(plugin, latch));
		plugins.clear();
		simplexPlugins.clear();
		duplexPlugins.clear();
		// Wait for all the plugins to stop
		try {
			latch.await();
		} catch (InterruptedException e) {
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
		for (DuplexPlugin d : duplexPlugins)
			if (d.supportsInvitations()) supported.add(d);
		return Collections.unmodifiableList(supported);
	}

	public Collection<DuplexPlugin> getKeyAgreementPlugins() {
		List<DuplexPlugin> supported = new ArrayList<DuplexPlugin>();
		for (DuplexPlugin d : duplexPlugins)
			if (d.supportsKeyAgreement()) supported.add(d);
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
				if (plugin == null) {
					if (LOG.isLoggable(INFO)) {
						String name = factory.getClass().getSimpleName();
						LOG.info(name + " did not create a plugin");
					}
					return;
				}
				try {
					long start = clock.currentTimeMillis();
					Transaction txn = db.startTransaction();
					try {
						db.addTransport(txn, id, plugin.getMaxLatency());
						txn.setComplete();
					} finally {
						db.endTransaction(txn);
					}
					long duration = clock.currentTimeMillis() - start;
					if (LOG.isLoggable(INFO))
						LOG.info("Adding transport took " + duration + " ms");
				} catch (DbException e) {
					if (LOG.isLoggable(WARNING))
						LOG.log(WARNING, e.toString(), e);
					return;
				}
				try {
					long start = clock.currentTimeMillis();
					boolean started = plugin.start();
					long duration = clock.currentTimeMillis() - start;
					if (started) {
						plugins.put(id, plugin);
						simplexPlugins.add(plugin);
						if (plugin.shouldPoll()) poller.addPlugin(plugin);
						if (LOG.isLoggable(INFO)) {
							String name = plugin.getClass().getSimpleName();
							LOG.info("Starting " + name + " took " +
									duration + " ms");
						}
					} else {
						if (LOG.isLoggable(WARNING)) {
							String name = plugin.getClass().getSimpleName();
							LOG.warning(name + " did not start");
						}
					}
				} catch (IOException e) {
					if (LOG.isLoggable(WARNING))
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
				if (plugin == null) {
					if (LOG.isLoggable(INFO)) {
						String name = factory.getClass().getSimpleName();
						LOG.info(name + " did not create a plugin");
					}
					return;
				}
				try {
					long start = clock.currentTimeMillis();
					Transaction txn = db.startTransaction();
					try {
						db.addTransport(txn, id, plugin.getMaxLatency());
						txn.setComplete();
					} finally {
						db.endTransaction(txn);
					}
					long duration = clock.currentTimeMillis() - start;
					if (LOG.isLoggable(INFO))
						LOG.info("Adding transport took " + duration + " ms");
				} catch (DbException e) {
					if (LOG.isLoggable(WARNING))
						LOG.log(WARNING, e.toString(), e);
					return;
				}
				try {
					long start = clock.currentTimeMillis();
					boolean started = plugin.start();
					long duration = clock.currentTimeMillis() - start;
					if (started) {
						plugins.put(id, plugin);
						duplexPlugins.add(plugin);
						if (plugin.shouldPoll()) poller.addPlugin(plugin);
						if (LOG.isLoggable(INFO)) {
							String name = plugin.getClass().getSimpleName();
							LOG.info("Starting " + name + " took " +
									duration + " ms");
						}
					} else {
						if (LOG.isLoggable(WARNING)) {
							String name = plugin.getClass().getSimpleName();
							LOG.warning(name + " did not start");
						}
					}
				} catch (IOException e) {
					if (LOG.isLoggable(WARNING))
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
				if (LOG.isLoggable(INFO)) {
					String name = plugin.getClass().getSimpleName();
					LOG.info("Stopping " + name + " took " + duration + " ms");
				}
			} catch (IOException e) {
				if (LOG.isLoggable(WARNING)) LOG.log(WARNING, e.toString(), e);
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

		public Settings getSettings() {
			try {
				return settingsManager.getSettings(id.getString());
			} catch (DbException e) {
				if (LOG.isLoggable(WARNING)) LOG.log(WARNING, e.toString(), e);
				return new Settings();
			}
		}

		public TransportProperties getLocalProperties() {
			try {
				TransportProperties p =
						transportPropertyManager.getLocalProperties(id);
				return p == null ? new TransportProperties() : p;
			} catch (DbException e) {
				if (LOG.isLoggable(WARNING)) LOG.log(WARNING, e.toString(), e);
				return new TransportProperties();
			}
		}

		public Map<ContactId, TransportProperties> getRemoteProperties() {
			try {
				return transportPropertyManager.getRemoteProperties(id);
			} catch (DbException e) {
				if (LOG.isLoggable(WARNING)) LOG.log(WARNING, e.toString(), e);
				return Collections.emptyMap();
			}
		}

		public void mergeSettings(Settings s) {
			try {
				settingsManager.mergeSettings(s, id.getString());
			} catch (DbException e) {
				if (LOG.isLoggable(WARNING)) LOG.log(WARNING, e.toString(), e);
			}
		}

		public void mergeLocalProperties(TransportProperties p) {
			try {
				transportPropertyManager.mergeLocalProperties(id, p);
			} catch (DbException e) {
				if (LOG.isLoggable(WARNING)) LOG.log(WARNING, e.toString(), e);
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

		public void transportEnabled() {
			eventBus.broadcast(new TransportEnabledEvent(id));
			Plugin p = plugins.get(id);
			if (p != null) poller.pollNow(p);
		}

		public void transportDisabled() {
			eventBus.broadcast(new TransportDisabledEvent(id));
		}
	}

	private class SimplexCallback extends PluginCallbackImpl
			implements SimplexPluginCallback {

		private SimplexCallback(TransportId id) {
			super(id);
		}

		public void readerCreated(TransportConnectionReader r) {
			connectionManager.manageIncomingConnection(id, r);
		}

		public void writerCreated(ContactId c, TransportConnectionWriter w) {
			connectionManager.manageOutgoingConnection(c, id, w);
		}
	}

	private class DuplexCallback extends PluginCallbackImpl
			implements DuplexPluginCallback {

		private DuplexCallback(TransportId id) {
			super(id);
		}

		public void incomingConnectionCreated(DuplexTransportConnection d) {
			connectionManager.manageIncomingConnection(id, d);
		}

		public void outgoingConnectionCreated(ContactId c,
				DuplexTransportConnection d) {
			connectionManager.manageOutgoingConnection(c, id, d);
		}
	}
}
