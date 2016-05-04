package org.briarproject.plugins;

import org.briarproject.api.TransportId;
import org.briarproject.api.contact.ContactId;
import org.briarproject.api.db.DbException;
import org.briarproject.api.event.ConnectionClosedEvent;
import org.briarproject.api.event.ContactStatusChangedEvent;
import org.briarproject.api.event.Event;
import org.briarproject.api.event.EventBus;
import org.briarproject.api.event.EventListener;
import org.briarproject.api.event.TransportDisabledEvent;
import org.briarproject.api.event.TransportEnabledEvent;
import org.briarproject.api.lifecycle.IoExecutor;
import org.briarproject.api.lifecycle.Service;
import org.briarproject.api.lifecycle.ServiceException;
import org.briarproject.api.plugins.ConnectionManager;
import org.briarproject.api.plugins.ConnectionRegistry;
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
import java.util.logging.Logger;

import javax.inject.Inject;

import static java.util.logging.Level.INFO;
import static java.util.logging.Level.WARNING;

class PluginManagerImpl implements PluginManager, Service, EventListener {

	private static final Logger LOG =
			Logger.getLogger(PluginManagerImpl.class.getName());

	private final Executor ioExecutor;
	private final EventBus eventBus;
	private final PluginConfig pluginConfig;
	private final Poller poller;
	private final ConnectionManager connectionManager;
	private final ConnectionRegistry connectionRegistry;
	private final SettingsManager settingsManager;
	private final TransportPropertyManager transportPropertyManager;
	private final UiCallback uiCallback;
	private final Map<TransportId, Plugin> plugins;
	private final List<SimplexPlugin> simplexPlugins;
	private final List<DuplexPlugin> duplexPlugins;

	@Inject
	PluginManagerImpl(@IoExecutor Executor ioExecutor, EventBus eventBus,
			PluginConfig pluginConfig, Poller poller,
			ConnectionManager connectionManager,
			ConnectionRegistry connectionRegistry,
			SettingsManager settingsManager,
			TransportPropertyManager transportPropertyManager,
			UiCallback uiCallback) {
		this.ioExecutor = ioExecutor;
		this.eventBus = eventBus;
		this.pluginConfig = pluginConfig;
		this.poller = poller;
		this.connectionManager = connectionManager;
		this.connectionRegistry = connectionRegistry;
		this.settingsManager = settingsManager;
		this.transportPropertyManager = transportPropertyManager;
		this.uiCallback = uiCallback;
		plugins = new ConcurrentHashMap<TransportId, Plugin>();
		simplexPlugins = new CopyOnWriteArrayList<SimplexPlugin>();
		duplexPlugins = new CopyOnWriteArrayList<DuplexPlugin>();
	}

	@Override
	public void startService() throws ServiceException {
		// Instantiate and start the simplex plugins
		LOG.info("Starting simplex plugins");
		Collection<SimplexPluginFactory> sFactories =
				pluginConfig.getSimplexFactories();
		final CountDownLatch sLatch = new CountDownLatch(sFactories.size());
		for (SimplexPluginFactory factory : sFactories)
			ioExecutor.execute(new SimplexPluginStarter(factory, sLatch));
		// Instantiate and start the duplex plugins
		LOG.info("Starting duplex plugins");
		Collection<DuplexPluginFactory> dFactories =
				pluginConfig.getDuplexFactories();
		final CountDownLatch dLatch = new CountDownLatch(dFactories.size());
		for (DuplexPluginFactory factory : dFactories)
			ioExecutor.execute(new DuplexPluginStarter(factory, dLatch));
		// Wait for the plugins to start
		try {
			sLatch.await();
			dLatch.await();
		} catch (InterruptedException e) {
			throw new ServiceException(e);
		}
		// Listen for events
		eventBus.addListener(this);
	}

	@Override
	public void stopService() throws ServiceException {
		// Stop listening for events
		eventBus.removeListener(this);
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
		// Wait for all the plugins to stop
		try {
			latch.await();
		} catch (InterruptedException e) {
			throw new ServiceException(e);
		}
	}

	@Override
	public Plugin getPlugin(TransportId t) {
		return plugins.get(t);
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

	@Override
	public void eventOccurred(Event e) {
		if (e instanceof ContactStatusChangedEvent) {
			ContactStatusChangedEvent c = (ContactStatusChangedEvent) e;
			if (c.isActive()) {
				// Connect to the newly activated contact
				connectToContact(c.getContactId());
			}
		} else if (e instanceof ConnectionClosedEvent) {
			ConnectionClosedEvent c = (ConnectionClosedEvent) e;
			if (!c.isIncoming()) {
				// Connect to the disconnected contact
				connectToContact(c.getContactId(), c.getTransportId());
			}
		}
	}

	private void connectToContact(ContactId c) {
		for (SimplexPlugin s : simplexPlugins)
			if (s.shouldPoll()) connectToContact(c, s);
		for (DuplexPlugin d : duplexPlugins)
			if (d.shouldPoll()) connectToContact(c, d);
	}

	private void connectToContact(ContactId c, TransportId t) {
		Plugin p = plugins.get(t);
		if (p instanceof SimplexPlugin && p.shouldPoll())
			connectToContact(c, (SimplexPlugin) p);
		else if (p instanceof DuplexPlugin && p.shouldPoll())
			connectToContact(c, (DuplexPlugin) p);
	}

	private void connectToContact(final ContactId c, final SimplexPlugin p) {
		ioExecutor.execute(new Runnable() {
			@Override
			public void run() {
				TransportId t = p.getId();
				if (!connectionRegistry.isConnected(c, t)) {
					TransportConnectionWriter w = p.createWriter(c);
					if (w != null)
						connectionManager.manageOutgoingConnection(c, t, w);
				}
			}
		});
	}

	private void connectToContact(final ContactId c, final DuplexPlugin p) {
		ioExecutor.execute(new Runnable() {
			@Override
			public void run() {
				TransportId t = p.getId();
				if (!connectionRegistry.isConnected(c, t)) {
					DuplexTransportConnection d = p.createConnection(c);
					if (d != null)
						connectionManager.manageOutgoingConnection(c, t, d);
				}
			}
		});
	}

	private class SimplexPluginStarter implements Runnable {

		private final SimplexPluginFactory factory;
		private final CountDownLatch latch;

		private SimplexPluginStarter(SimplexPluginFactory factory,
				CountDownLatch latch) {
			this.factory = factory;
			this.latch = latch;
		}

		@Override
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
					long start = System.currentTimeMillis();
					boolean started = plugin.start();
					long duration = System.currentTimeMillis() - start;
					if (started) {
						plugins.put(id, plugin);
						simplexPlugins.add(plugin);
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

		@Override
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
					long start = System.currentTimeMillis();
					boolean started = plugin.start();
					long duration = System.currentTimeMillis() - start;
					if (started) {
						plugins.put(id, plugin);
						duplexPlugins.add(plugin);
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

		@Override
		public void run() {
			try {
				long start = System.currentTimeMillis();
				plugin.stop();
				long duration = System.currentTimeMillis() - start;
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
			Plugin p = plugins.get(id);
			if (p != null) poller.pollNow(p);
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
