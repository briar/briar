package net.sf.briar.plugins;

import static java.util.logging.Level.INFO;
import static java.util.logging.Level.WARNING;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.logging.Logger;

import javax.inject.Inject;

import net.sf.briar.api.ContactId;
import net.sf.briar.api.TransportConfig;
import net.sf.briar.api.TransportId;
import net.sf.briar.api.TransportProperties;
import net.sf.briar.api.db.DatabaseComponent;
import net.sf.briar.api.db.DbException;
import net.sf.briar.api.plugins.Plugin;
import net.sf.briar.api.plugins.PluginCallback;
import net.sf.briar.api.plugins.PluginExecutor;
import net.sf.briar.api.plugins.PluginManager;
import net.sf.briar.api.plugins.duplex.DuplexPlugin;
import net.sf.briar.api.plugins.duplex.DuplexPluginCallback;
import net.sf.briar.api.plugins.duplex.DuplexPluginConfig;
import net.sf.briar.api.plugins.duplex.DuplexPluginFactory;
import net.sf.briar.api.plugins.duplex.DuplexTransportConnection;
import net.sf.briar.api.plugins.simplex.SimplexPlugin;
import net.sf.briar.api.plugins.simplex.SimplexPluginCallback;
import net.sf.briar.api.plugins.simplex.SimplexPluginConfig;
import net.sf.briar.api.plugins.simplex.SimplexPluginFactory;
import net.sf.briar.api.plugins.simplex.SimplexTransportReader;
import net.sf.briar.api.plugins.simplex.SimplexTransportWriter;
import net.sf.briar.api.transport.ConnectionDispatcher;
import net.sf.briar.api.ui.UiCallback;

// FIXME: Don't make alien calls with a lock held (that includes waiting on a
// latch that depends on an alien call)
class PluginManagerImpl implements PluginManager {

	private static final Logger LOG =
			Logger.getLogger(PluginManagerImpl.class.getName());

	private final Executor pluginExecutor;
	private final SimplexPluginConfig simplexPluginConfig;
	private final DuplexPluginConfig duplexPluginConfig;
	private final DatabaseComponent db;
	private final Poller poller;
	private final ConnectionDispatcher dispatcher;
	private final UiCallback uiCallback;
	private final List<SimplexPlugin> simplexPlugins;
	private final List<DuplexPlugin> duplexPlugins;

	@Inject
	PluginManagerImpl(@PluginExecutor Executor pluginExecutor,
			SimplexPluginConfig simplexPluginConfig, 
			DuplexPluginConfig duplexPluginConfig, DatabaseComponent db,
			Poller poller, ConnectionDispatcher dispatcher,
			UiCallback uiCallback) {
		this.pluginExecutor = pluginExecutor;
		this.simplexPluginConfig = simplexPluginConfig;
		this.duplexPluginConfig = duplexPluginConfig;
		this.db = db;
		this.poller = poller;
		this.dispatcher = dispatcher;
		this.uiCallback = uiCallback;
		simplexPlugins = new CopyOnWriteArrayList<SimplexPlugin>();
		duplexPlugins = new CopyOnWriteArrayList<DuplexPlugin>();
	}

	public synchronized boolean start() {
		// Instantiate and start the simplex plugins
		if(LOG.isLoggable(INFO)) LOG.info("Starting simplex plugins");
		Collection<SimplexPluginFactory> sFactories =
				simplexPluginConfig.getFactories();
		final CountDownLatch sLatch = new CountDownLatch(sFactories.size());
		for(SimplexPluginFactory factory : sFactories)
			pluginExecutor.execute(new SimplexPluginStarter(factory, sLatch));
		// Instantiate and start the duplex plugins
		if(LOG.isLoggable(INFO)) LOG.info("Starting duplex plugins");
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
			if(LOG.isLoggable(WARNING))
				LOG.warning("Interrupted while starting plugins");
			Thread.currentThread().interrupt();
			return false;
		}
		// Start the poller
		if(LOG.isLoggable(INFO)) LOG.info("Starting poller");
		List<Plugin> plugins = new ArrayList<Plugin>();
		plugins.addAll(simplexPlugins);
		plugins.addAll(duplexPlugins);
		poller.start(Collections.unmodifiableList(plugins));
		return true;
	}

	public synchronized boolean stop() {
		// Stop the poller
		if(LOG.isLoggable(INFO)) LOG.info("Stopping poller");
		poller.stop();
		int plugins = simplexPlugins.size() + duplexPlugins.size();
		final CountDownLatch latch = new CountDownLatch(plugins);
		// Stop the simplex plugins
		if(LOG.isLoggable(INFO)) LOG.info("Stopping simplex plugins");
		for(SimplexPlugin plugin : simplexPlugins)
			pluginExecutor.execute(new PluginStopper(plugin, latch));
		// Stop the duplex plugins
		if(LOG.isLoggable(INFO)) LOG.info("Stopping duplex plugins");
		for(DuplexPlugin plugin : duplexPlugins)
			pluginExecutor.execute(new PluginStopper(plugin, latch));
		simplexPlugins.clear();
		duplexPlugins.clear();
		// Wait for all the plugins to stop
		try {
			latch.await();
		} catch(InterruptedException e) {
			if(LOG.isLoggable(WARNING))
				LOG.warning("Interrupted while stopping plugins");
			Thread.currentThread().interrupt();
			return false;
		}
		return true;
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
					db.addTransport(id, plugin.getMaxLatency());
				} catch(DbException e) {
					if(LOG.isLoggable(WARNING))
						LOG.log(WARNING, e.toString(), e);
					return;
				}
				try {
					if(plugin.start()) {
						simplexPlugins.add(plugin);
					} else {
						if(LOG.isLoggable(INFO)) {
							String name = plugin.getClass().getSimpleName();
							LOG.info(name + " did not start");
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
					db.addTransport(id, plugin.getMaxLatency());
				} catch(DbException e) {
					if(LOG.isLoggable(WARNING))
						LOG.log(WARNING, e.toString(), e);
					return;
				}
				try {
					if(plugin.start()) {
						duplexPlugins.add(plugin);
					} else {
						if(LOG.isLoggable(INFO)) {
							String name = plugin.getClass().getSimpleName();
							LOG.info(name + " did not start");
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
				plugin.stop();
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