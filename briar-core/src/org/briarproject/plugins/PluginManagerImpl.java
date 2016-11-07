package org.briarproject.plugins;

import org.briarproject.api.TransportId;
import org.briarproject.api.contact.ContactId;
import org.briarproject.api.db.DbException;
import org.briarproject.api.event.EventBus;
import org.briarproject.api.event.TransportDisabledEvent;
import org.briarproject.api.event.TransportEnabledEvent;
import org.briarproject.api.lifecycle.IoExecutor;
import org.briarproject.api.lifecycle.Service;
import org.briarproject.api.lifecycle.ServiceException;
import org.briarproject.api.nullsafety.NotNullByDefault;
import org.briarproject.api.plugins.ConnectionManager;
import org.briarproject.api.plugins.Plugin;
import org.briarproject.api.plugins.PluginCallback;
import org.briarproject.api.plugins.PluginConfig;
import org.briarproject.api.plugins.PluginManager;
import org.briarproject.api.plugins.TransportConnectionReader;
import org.briarproject.api.plugins.TransportConnectionWriter;
import org.briarproject.api.plugins.duplex.DuplexPlugin;
import org.briarproject.api.plugins.duplex.DuplexPluginCallback;
import org.briarproject.api.plugins.duplex.DuplexPluginFactory;
import org.briarproject.api.plugins.duplex.DuplexTransportConnection;
import org.briarproject.api.plugins.simplex.SimplexPlugin;
import org.briarproject.api.plugins.simplex.SimplexPluginCallback;
import org.briarproject.api.plugins.simplex.SimplexPluginFactory;
import org.briarproject.api.properties.TransportProperties;
import org.briarproject.api.properties.TransportPropertyManager;
import org.briarproject.api.settings.Settings;
import org.briarproject.api.settings.SettingsManager;
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
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;

import javax.annotation.concurrent.ThreadSafe;
import javax.inject.Inject;

import static java.util.logging.Level.INFO;
import static java.util.logging.Level.WARNING;

@ThreadSafe
@NotNullByDefault
class PluginManagerImpl implements PluginManager, Service {

	private static final Logger LOG =
			Logger.getLogger(PluginManagerImpl.class.getName());

	private final Executor ioExecutor;
	private final EventBus eventBus;
	private final PluginConfig pluginConfig;
	private final ConnectionManager connectionManager;
	private final SettingsManager settingsManager;
	private final TransportPropertyManager transportPropertyManager;
	private final UiCallback uiCallback;
	private final Map<TransportId, Plugin> plugins;
	private final List<SimplexPlugin> simplexPlugins;
	private final List<DuplexPlugin> duplexPlugins;
	private final Map<TransportId, CountDownLatch> startLatches;
	private final AtomicBoolean used = new AtomicBoolean(false);

	@Inject
	PluginManagerImpl(@IoExecutor Executor ioExecutor, EventBus eventBus,
			PluginConfig pluginConfig, ConnectionManager connectionManager,
			SettingsManager settingsManager,
			TransportPropertyManager transportPropertyManager,
			UiCallback uiCallback) {
		this.ioExecutor = ioExecutor;
		this.eventBus = eventBus;
		this.pluginConfig = pluginConfig;
		this.connectionManager = connectionManager;
		this.settingsManager = settingsManager;
		this.transportPropertyManager = transportPropertyManager;
		this.uiCallback = uiCallback;
		plugins = new ConcurrentHashMap<TransportId, Plugin>();
		simplexPlugins = new CopyOnWriteArrayList<SimplexPlugin>();
		duplexPlugins = new CopyOnWriteArrayList<DuplexPlugin>();
		startLatches = new ConcurrentHashMap<TransportId, CountDownLatch>();
	}

	@Override
	public void startService() throws ServiceException {
		if (used.getAndSet(true)) throw new IllegalStateException();
		// Instantiate the simplex plugins and start them asynchronously
		LOG.info("Starting simplex plugins");
		for (SimplexPluginFactory f : pluginConfig.getSimplexFactories()) {
			TransportId t = f.getId();
			SimplexPlugin s = f.createPlugin(new SimplexCallback(t));
			if (s == null) {
				if (LOG.isLoggable(WARNING))
					LOG.warning("Could not create plugin for " + t);
			} else {
				plugins.put(t, s);
				simplexPlugins.add(s);
				CountDownLatch startLatch = new CountDownLatch(1);
				startLatches.put(t, startLatch);
				ioExecutor.execute(new PluginStarter(s, startLatch));
			}
		}
		// Instantiate the duplex plugins and start them asynchronously
		LOG.info("Starting duplex plugins");
		for (DuplexPluginFactory f : pluginConfig.getDuplexFactories()) {
			TransportId t = f.getId();
			DuplexPlugin d = f.createPlugin(new DuplexCallback(t));
			if (d == null) {
				if (LOG.isLoggable(WARNING))
					LOG.warning("Could not create plugin for " + t);
			} else {
				plugins.put(t, d);
				duplexPlugins.add(d);
				CountDownLatch startLatch = new CountDownLatch(1);
				startLatches.put(t, startLatch);
				ioExecutor.execute(new PluginStarter(d, startLatch));
			}
		}
	}

	@Override
	public void stopService() throws ServiceException {
		CountDownLatch stopLatch = new CountDownLatch(plugins.size());
		// Stop the simplex plugins
		LOG.info("Stopping simplex plugins");
		for (SimplexPlugin s : simplexPlugins) {
			CountDownLatch startLatch = startLatches.get(s.getId());
			ioExecutor.execute(new PluginStopper(s, startLatch, stopLatch));
		}
		// Stop the duplex plugins
		LOG.info("Stopping duplex plugins");
		for (DuplexPlugin d : duplexPlugins) {
			CountDownLatch startLatch = startLatches.get(d.getId());
			ioExecutor.execute(new PluginStopper(d, startLatch, stopLatch));
		}
		// Wait for all the plugins to stop
		try {
			LOG.info("Waiting for all the plugins to stop");
			stopLatch.await();
		} catch (InterruptedException e) {
			throw new ServiceException(e);
		}
	}

	@Override
	public Plugin getPlugin(TransportId t) {
		return plugins.get(t);
	}

	@Override
	public Collection<SimplexPlugin> getSimplexPlugins() {
		return Collections.unmodifiableList(simplexPlugins);
	}

	@Override
	public Collection<DuplexPlugin> getDuplexPlugins() {
		return Collections.unmodifiableList(duplexPlugins);
	}

	@Override
	public Collection<DuplexPlugin> getInvitationPlugins() {
		List<DuplexPlugin> supported = new ArrayList<DuplexPlugin>();
		for (DuplexPlugin d : duplexPlugins)
			if (d.supportsInvitations()) supported.add(d);
		return Collections.unmodifiableList(supported);
	}

	@Override
	public Collection<DuplexPlugin> getKeyAgreementPlugins() {
		List<DuplexPlugin> supported = new ArrayList<DuplexPlugin>();
		for (DuplexPlugin d : duplexPlugins)
			if (d.supportsKeyAgreement()) supported.add(d);
		return Collections.unmodifiableList(supported);
	}

	private class PluginStarter implements Runnable {

		private final Plugin plugin;
		private final CountDownLatch startLatch;

		private PluginStarter(Plugin plugin, CountDownLatch startLatch) {
			this.plugin = plugin;
			this.startLatch = startLatch;
		}

		@Override
		public void run() {
			try {
				try {
					long start = System.currentTimeMillis();
					boolean started = plugin.start();
					long duration = System.currentTimeMillis() - start;
					if (started) {
						if (LOG.isLoggable(INFO)) {
							LOG.info("Starting plugin " + plugin.getId()
									+ " took " + duration + " ms");
						}
					} else {
						if (LOG.isLoggable(WARNING)) {
							LOG.warning("Plugin" + plugin.getId()
									+ " did not start");
						}
					}
				} catch (IOException e) {
					if (LOG.isLoggable(WARNING))
						LOG.log(WARNING, e.toString(), e);
				}
			} finally {
				startLatch.countDown();
			}
		}
	}

	private class PluginStopper implements Runnable {

		private final Plugin plugin;
		private final CountDownLatch startLatch, stopLatch;

		private PluginStopper(Plugin plugin, CountDownLatch startLatch,
				CountDownLatch stopLatch) {
			this.plugin = plugin;
			this.startLatch = startLatch;
			this.stopLatch = stopLatch;
		}

		@Override
		public void run() {
			if (LOG.isLoggable(INFO))
				LOG.info("Trying to stop plugin " + plugin.getId());
			try {
				// Wait for the plugin to finish starting
				startLatch.await();
				// Stop the plugin
				long start = System.currentTimeMillis();
				plugin.stop();
				long duration = System.currentTimeMillis() - start;
				if (LOG.isLoggable(INFO)) {
					LOG.info("Stopping plugin " + plugin.getId()
							+ " took " + duration + " ms");
				}
			} catch (InterruptedException e) {
				LOG.warning("Interrupted while waiting for plugin to start");
				// This task runs on an executor, so don't reset the interrupt
			} catch (IOException e) {
				if (LOG.isLoggable(WARNING)) LOG.log(WARNING, e.toString(), e);
			} finally {
				stopLatch.countDown();
			}
		}
	}

	private abstract class PluginCallbackImpl implements PluginCallback {

		protected final TransportId id;

		protected PluginCallbackImpl(TransportId id) {
			this.id = id;
		}

		@Override
		public Settings getSettings() {
			try {
				return settingsManager.getSettings(id.getString());
			} catch (DbException e) {
				if (LOG.isLoggable(WARNING)) LOG.log(WARNING, e.toString(), e);
				return new Settings();
			}
		}

		@Override
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

		@Override
		public Map<ContactId, TransportProperties> getRemoteProperties() {
			try {
				return transportPropertyManager.getRemoteProperties(id);
			} catch (DbException e) {
				if (LOG.isLoggable(WARNING)) LOG.log(WARNING, e.toString(), e);
				return Collections.emptyMap();
			}
		}

		@Override
		public void mergeSettings(Settings s) {
			try {
				settingsManager.mergeSettings(s, id.getString());
			} catch (DbException e) {
				if (LOG.isLoggable(WARNING)) LOG.log(WARNING, e.toString(), e);
			}
		}

		@Override
		public void mergeLocalProperties(TransportProperties p) {
			try {
				transportPropertyManager.mergeLocalProperties(id, p);
			} catch (DbException e) {
				if (LOG.isLoggable(WARNING)) LOG.log(WARNING, e.toString(), e);
			}
		}

		@Override
		public int showChoice(String[] options, String... message) {
			return uiCallback.showChoice(options, message);
		}

		@Override
		public boolean showConfirmationMessage(String... message) {
			return uiCallback.showConfirmationMessage(message);
		}

		@Override
		public void showMessage(String... message) {
			uiCallback.showMessage(message);
		}

		@Override
		public void transportEnabled() {
			eventBus.broadcast(new TransportEnabledEvent(id));
		}

		@Override
		public void transportDisabled() {
			eventBus.broadcast(new TransportDisabledEvent(id));
		}
	}

	private class SimplexCallback extends PluginCallbackImpl
			implements SimplexPluginCallback {

		private SimplexCallback(TransportId id) {
			super(id);
		}

		@Override
		public void readerCreated(TransportConnectionReader r) {
			connectionManager.manageIncomingConnection(id, r);
		}

		@Override
		public void writerCreated(ContactId c, TransportConnectionWriter w) {
			connectionManager.manageOutgoingConnection(c, id, w);
		}
	}

	private class DuplexCallback extends PluginCallbackImpl
			implements DuplexPluginCallback {

		private DuplexCallback(TransportId id) {
			super(id);
		}

		@Override
		public void incomingConnectionCreated(DuplexTransportConnection d) {
			connectionManager.manageIncomingConnection(id, d);
		}

		@Override
		public void outgoingConnectionCreated(ContactId c,
				DuplexTransportConnection d) {
			connectionManager.manageOutgoingConnection(c, id, d);
		}
	}
}
