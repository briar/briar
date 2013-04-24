package net.sf.briar.plugins;

import static java.util.logging.Level.INFO;
import static java.util.logging.Level.WARNING;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.logging.Logger;

import net.sf.briar.api.ContactId;
import net.sf.briar.api.TransportConfig;
import net.sf.briar.api.TransportId;
import net.sf.briar.api.TransportProperties;
import net.sf.briar.api.android.AndroidExecutor;
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

import com.google.inject.Inject;

class PluginManagerImpl implements PluginManager {

	private static final Logger LOG =
			Logger.getLogger(PluginManagerImpl.class.getName());

	private final ExecutorService pluginExecutor;
	private final AndroidExecutor androidExecutor;
	private final SimplexPluginConfig simplexPluginConfig;
	private final DuplexPluginConfig duplexPluginConfig;
	private final DatabaseComponent db;
	private final Poller poller;
	private final ConnectionDispatcher dispatcher;
	private final UiCallback uiCallback;
	private final List<SimplexPlugin> simplexPlugins; // Locking: this
	private final List<DuplexPlugin> duplexPlugins; // Locking: this

	@Inject
	PluginManagerImpl(@PluginExecutor ExecutorService pluginExecutor,
			AndroidExecutor androidExecutor,
			SimplexPluginConfig simplexPluginConfig, 
			DuplexPluginConfig duplexPluginConfig, DatabaseComponent db,
			Poller poller, ConnectionDispatcher dispatcher,
			UiCallback uiCallback) {
		this.pluginExecutor = pluginExecutor;
		this.androidExecutor = androidExecutor;
		this.simplexPluginConfig = simplexPluginConfig;
		this.duplexPluginConfig = duplexPluginConfig;
		this.db = db;
		this.poller = poller;
		this.dispatcher = dispatcher;
		this.uiCallback = uiCallback;
		simplexPlugins = new ArrayList<SimplexPlugin>();
		duplexPlugins = new ArrayList<DuplexPlugin>();
	}

	public synchronized int start() {
		Set<TransportId> ids = new HashSet<TransportId>();
		// Instantiate and start the simplex plugins
		if(LOG.isLoggable(INFO)) LOG.info("Starting simplex plugins");
		for(SimplexPluginFactory factory : simplexPluginConfig.getFactories()) {
			TransportId id = factory.getId();
			if(!ids.add(id)) {
				if(LOG.isLoggable(WARNING))
					LOG.warning("Duplicate transport ID: " + id);
				continue;
			}
			SimplexCallback callback = new SimplexCallback(id);
			SimplexPlugin plugin = factory.createPlugin(callback);
			if(plugin == null) {
				if(LOG.isLoggable(INFO)) {
					LOG.info(factory.getClass().getSimpleName()
							+ " did not create a plugin");
				}
				continue;
			}
			try {
				db.addTransport(id, plugin.getMaxLatency());
			} catch(DbException e) {
				if(LOG.isLoggable(WARNING)) LOG.log(WARNING, e.toString(), e);
				continue;
			}
			try {
				if(plugin.start()) {
					simplexPlugins.add(plugin);
				} else {
					if(LOG.isLoggable(INFO))
						LOG.info(plugin.getClass().getSimpleName()
								+ " did not start");
				}
			} catch(IOException e) {
				if(LOG.isLoggable(WARNING)) LOG.log(WARNING, e.toString(), e);
			}
		}
		// Instantiate and start the duplex plugins
		if(LOG.isLoggable(INFO)) LOG.info("Starting duplex plugins");
		for(DuplexPluginFactory factory : duplexPluginConfig.getFactories()) {
			TransportId id = factory.getId();
			if(!ids.add(id)) {
				if(LOG.isLoggable(WARNING))
					LOG.warning("Duplicate transport ID: " + id);
				continue;
			}
			DuplexCallback callback = new DuplexCallback(id);
			DuplexPlugin plugin = factory.createPlugin(callback);
			if(plugin == null) {
				if(LOG.isLoggable(INFO)) {
					LOG.info(factory.getClass().getSimpleName()
							+ " did not create a plugin");
				}
				continue;
			}
			try {
				db.addTransport(id, plugin.getMaxLatency());
			} catch(DbException e) {
				if(LOG.isLoggable(WARNING)) LOG.log(WARNING, e.toString(), e);
				continue;
			}
			try {
				if(plugin.start()) {
					duplexPlugins.add(plugin);
				} else {
					if(LOG.isLoggable(INFO))
						LOG.info(plugin.getClass().getSimpleName()
								+ " did not start");
				}
			} catch(IOException e) {
				if(LOG.isLoggable(WARNING)) LOG.log(WARNING, e.toString(), e);
			}
		}
		// Start the poller
		if(LOG.isLoggable(INFO)) LOG.info("Starting poller");
		List<Plugin> plugins = new ArrayList<Plugin>();
		plugins.addAll(simplexPlugins);
		plugins.addAll(duplexPlugins);
		poller.start(Collections.unmodifiableList(plugins));
		// Return the number of plugins successfully started
		return simplexPlugins.size() + duplexPlugins.size();
	}

	public synchronized int stop() {
		int stopped = 0;
		// Stop the poller
		if(LOG.isLoggable(INFO)) LOG.info("Stopping poller");
		poller.stop();
		// Stop the simplex plugins
		if(LOG.isLoggable(INFO)) LOG.info("Stopping simplex plugins");
		for(SimplexPlugin plugin : simplexPlugins) {
			try {
				plugin.stop();
				stopped++;
			} catch(IOException e) {
				if(LOG.isLoggable(WARNING)) LOG.log(WARNING, e.toString(), e);
			}
		}
		simplexPlugins.clear();
		// Stop the duplex plugins
		if(LOG.isLoggable(INFO)) LOG.info("Stopping duplex plugins");
		for(DuplexPlugin plugin : duplexPlugins) {
			try {
				plugin.stop();
				stopped++;
			} catch(IOException e) {
				if(LOG.isLoggable(WARNING)) LOG.log(WARNING, e.toString(), e);
			}
		}
		duplexPlugins.clear();
		// Shut down the executors
		if(LOG.isLoggable(INFO)) LOG.info("Stopping executors");
		pluginExecutor.shutdown();
		androidExecutor.shutdown();
		// Return the number of plugins successfully stopped
		return stopped;
	}

	public synchronized Collection<DuplexPlugin> getInvitationPlugins() {
		List<DuplexPlugin> supported = new ArrayList<DuplexPlugin>();
		for(DuplexPlugin d : duplexPlugins)
			if(d.supportsInvitations()) supported.add(d);
		return Collections.unmodifiableList(supported);
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