package org.briarproject.bramble.plugin;

import org.briarproject.bramble.api.contact.ContactId;
import org.briarproject.bramble.api.db.DbException;
import org.briarproject.bramble.api.event.EventBus;
import org.briarproject.bramble.api.lifecycle.IoExecutor;
import org.briarproject.bramble.api.lifecycle.Service;
import org.briarproject.bramble.api.lifecycle.ServiceException;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.plugin.ConnectionManager;
import org.briarproject.bramble.api.plugin.Plugin;
import org.briarproject.bramble.api.plugin.PluginCallback;
import org.briarproject.bramble.api.plugin.PluginConfig;
import org.briarproject.bramble.api.plugin.PluginException;
import org.briarproject.bramble.api.plugin.PluginManager;
import org.briarproject.bramble.api.plugin.TransportConnectionReader;
import org.briarproject.bramble.api.plugin.TransportConnectionWriter;
import org.briarproject.bramble.api.plugin.TransportId;
import org.briarproject.bramble.api.plugin.duplex.DuplexPlugin;
import org.briarproject.bramble.api.plugin.duplex.DuplexPluginCallback;
import org.briarproject.bramble.api.plugin.duplex.DuplexPluginFactory;
import org.briarproject.bramble.api.plugin.duplex.DuplexTransportConnection;
import org.briarproject.bramble.api.plugin.event.TransportDisabledEvent;
import org.briarproject.bramble.api.plugin.event.TransportEnabledEvent;
import org.briarproject.bramble.api.plugin.simplex.SimplexPlugin;
import org.briarproject.bramble.api.plugin.simplex.SimplexPluginCallback;
import org.briarproject.bramble.api.plugin.simplex.SimplexPluginFactory;
import org.briarproject.bramble.api.properties.TransportProperties;
import org.briarproject.bramble.api.properties.TransportPropertyManager;
import org.briarproject.bramble.api.settings.Settings;
import org.briarproject.bramble.api.settings.SettingsManager;
import org.briarproject.bramble.api.ui.UiCallback;

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
		return new ArrayList<SimplexPlugin>(simplexPlugins);
	}

	@Override
	public Collection<DuplexPlugin> getDuplexPlugins() {
		return new ArrayList<DuplexPlugin>(duplexPlugins);
	}

	@Override
	public Collection<DuplexPlugin> getInvitationPlugins() {
		List<DuplexPlugin> supported = new ArrayList<DuplexPlugin>();
		for (DuplexPlugin d : duplexPlugins)
			if (d.supportsInvitations()) supported.add(d);
		return supported;
	}

	@Override
	public Collection<DuplexPlugin> getKeyAgreementPlugins() {
		List<DuplexPlugin> supported = new ArrayList<DuplexPlugin>();
		for (DuplexPlugin d : duplexPlugins)
			if (d.supportsKeyAgreement()) supported.add(d);
		return supported;
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
				long start = System.currentTimeMillis();
				plugin.start();
				long duration = System.currentTimeMillis() - start;
				if (LOG.isLoggable(INFO)) {
					LOG.info("Starting plugin " + plugin.getId() + " took " +
							duration + " ms");
				}
			} catch (PluginException e) {
				if (LOG.isLoggable(WARNING)) {
					LOG.warning("Plugin " + plugin.getId() + " did not start");
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
				LOG.warning("Interrupted while waiting for plugin to stop");
				// This task runs on an executor, so don't reset the interrupt
			} catch (PluginException e) {
				if (LOG.isLoggable(WARNING)) {
					LOG.warning("Plugin " + plugin.getId() + " did not stop");
					LOG.log(WARNING, e.toString(), e);
				}
			} finally {
				stopLatch.countDown();
			}
		}
	}

	@NotNullByDefault
	private abstract class PluginCallbackImpl implements PluginCallback {

		protected final TransportId id;

		PluginCallbackImpl(TransportId id) {
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
				return transportPropertyManager.getLocalProperties(id);
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

	@NotNullByDefault
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

	@NotNullByDefault
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
