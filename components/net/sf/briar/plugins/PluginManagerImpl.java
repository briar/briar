package net.sf.briar.plugins;

import static net.sf.briar.api.protocol.ProtocolConstants.MAX_PROPERTIES_PER_TRANSPORT;
import static net.sf.briar.api.protocol.ProtocolConstants.MAX_PROPERTY_LENGTH;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.logging.Level;
import java.util.logging.Logger;

import net.sf.briar.api.ContactId;
import net.sf.briar.api.TransportConfig;
import net.sf.briar.api.TransportProperties;
import net.sf.briar.api.db.DatabaseComponent;
import net.sf.briar.api.db.DbException;
import net.sf.briar.api.plugins.Plugin;
import net.sf.briar.api.plugins.PluginCallback;
import net.sf.briar.api.plugins.PluginExecutor;
import net.sf.briar.api.plugins.PluginManager;
import net.sf.briar.api.plugins.duplex.DuplexPlugin;
import net.sf.briar.api.plugins.duplex.DuplexPluginCallback;
import net.sf.briar.api.plugins.duplex.DuplexPluginFactory;
import net.sf.briar.api.plugins.duplex.DuplexTransportConnection;
import net.sf.briar.api.plugins.simplex.SimplexPlugin;
import net.sf.briar.api.plugins.simplex.SimplexPluginCallback;
import net.sf.briar.api.plugins.simplex.SimplexPluginFactory;
import net.sf.briar.api.plugins.simplex.SimplexTransportReader;
import net.sf.briar.api.plugins.simplex.SimplexTransportWriter;
import net.sf.briar.api.protocol.TransportId;
import net.sf.briar.api.transport.ConnectionDispatcher;
import net.sf.briar.api.ui.UiCallback;

import com.google.inject.Inject;

class PluginManagerImpl implements PluginManager {

	private static final Logger LOG =
			Logger.getLogger(PluginManagerImpl.class.getName());

	private static final String[] SIMPLEX_PLUGIN_FACTORIES = new String[] {
		"net.sf.briar.plugins.file.RemovableDrivePluginFactory"
	};

	private static final String[] DUPLEX_PLUGIN_FACTORIES = new String[] {
		"net.sf.briar.plugins.bluetooth.BluetoothPluginFactory",
		"net.sf.briar.plugins.socket.SimpleSocketPluginFactory",
		"net.sf.briar.plugins.tor.TorPluginFactory"
	};

	private final ExecutorService pluginExecutor;
	private final DatabaseComponent db;
	private final Poller poller;
	private final ConnectionDispatcher dispatcher;
	private final UiCallback uiCallback;
	private final List<SimplexPlugin> simplexPlugins; // Locking: this
	private final List<DuplexPlugin> duplexPlugins; // Locking: this

	@Inject
	PluginManagerImpl(@PluginExecutor ExecutorService pluginExecutor,
			DatabaseComponent db, Poller poller,
			ConnectionDispatcher dispatcher, UiCallback uiCallback) {
		this.pluginExecutor = pluginExecutor;
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
		for(String s : SIMPLEX_PLUGIN_FACTORIES) {
			try {
				Class<?> c = Class.forName(s);
				SimplexPluginFactory factory =
						(SimplexPluginFactory) c.newInstance();
				SimplexCallback callback = new SimplexCallback();
				SimplexPlugin plugin = factory.createPlugin(pluginExecutor,
						callback);
				if(plugin == null) {
					if(LOG.isLoggable(Level.INFO)) {
						LOG.info(factory.getClass().getSimpleName()
								+ " did not create a plugin");
					}
					continue;
				}
				TransportId id = plugin.getId();
				if(!ids.add(id)) {
					if(LOG.isLoggable(Level.WARNING))
						LOG.warning("Duplicate transport ID: " + id);
					continue;
				}
				callback.init(id);
				plugin.start();
				simplexPlugins.add(plugin);
			} catch(ClassCastException e) {
				if(LOG.isLoggable(Level.WARNING)) LOG.warning(e.toString());
				continue;
			} catch(Exception e) {
				if(LOG.isLoggable(Level.WARNING)) LOG.warning(e.toString());
				continue;
			}
		}
		// Instantiate and start the duplex plugins
		for(String s : DUPLEX_PLUGIN_FACTORIES) {
			try {
				Class<?> c = Class.forName(s);
				DuplexPluginFactory factory =
						(DuplexPluginFactory) c.newInstance();
				DuplexCallback callback = new DuplexCallback();
				DuplexPlugin plugin = factory.createPlugin(pluginExecutor,
						callback);
				if(plugin == null) {
					if(LOG.isLoggable(Level.INFO)) {
						LOG.info(factory.getClass().getSimpleName()
								+ " did not create a plugin");
					}
					continue;
				}
				TransportId id = plugin.getId();
				if(!ids.add(id)) {
					if(LOG.isLoggable(Level.WARNING))
						LOG.warning("Duplicate transport ID: " + id);
					continue;
				}
				callback.init(id);
				plugin.start();
				duplexPlugins.add(plugin);
			} catch(ClassCastException e) {
				if(LOG.isLoggable(Level.WARNING)) LOG.warning(e.toString());
				continue;
			} catch(Exception e) {
				if(LOG.isLoggable(Level.WARNING)) LOG.warning(e.toString());
				continue;
			}
		}
		// Start the poller
		List<Plugin> plugins = new ArrayList<Plugin>();
		plugins.addAll(simplexPlugins);
		plugins.addAll(duplexPlugins);
		poller.start(Collections.unmodifiableList(plugins));
		// Return the number of plugins successfully started
		return simplexPlugins.size() + duplexPlugins.size();
	}

	public synchronized int stop() {
		int stopped = 0;
		// Stop the simplex plugins
		for(SimplexPlugin plugin : simplexPlugins) {
			try {
				plugin.stop();
				stopped++;
			} catch(IOException e) {
				if(LOG.isLoggable(Level.WARNING)) LOG.warning(e.toString());
			}
		}
		simplexPlugins.clear();
		// Stop the duplex plugins
		for(DuplexPlugin plugin : duplexPlugins) {
			try {
				plugin.stop();
				stopped++;
			} catch(IOException e) {
				if(LOG.isLoggable(Level.WARNING)) LOG.warning(e.toString());
			}
		}
		duplexPlugins.clear();
		// Stop the poller
		poller.stop();
		// Shut down the executor service
		pluginExecutor.shutdown();
		// Return the number of plugins successfully stopped
		return stopped;
	}

	public Collection<DuplexPlugin> getInvitationPlugins() {
		Collection<DuplexPlugin> supported = new ArrayList<DuplexPlugin>();
		synchronized(this) {
			for(DuplexPlugin d : duplexPlugins) {
				if(d.supportsInvitations()) supported.add(d);
			}
		}
		return supported;
	}

	private abstract class PluginCallbackImpl implements PluginCallback {

		protected volatile TransportId id = null;

		protected void init(TransportId id) {
			assert this.id == null;
			this.id = id;
		}

		public TransportConfig getConfig() {
			assert id != null;
			try {
				return db.getConfig(id);
			} catch(DbException e) {
				if(LOG.isLoggable(Level.WARNING)) LOG.warning(e.toString());
				return new TransportConfig();
			}
		}

		public TransportProperties getLocalProperties() {
			assert id != null;
			try {
				TransportProperties p = db.getLocalProperties(id);
				return p == null ? new TransportProperties() : p;
			} catch(DbException e) {
				if(LOG.isLoggable(Level.WARNING)) LOG.warning(e.toString());
				return new TransportProperties();
			}
		}

		public Map<ContactId, TransportProperties> getRemoteProperties() {
			assert id != null;
			try {
				return db.getRemoteProperties(id);
			} catch(DbException e) {
				if(LOG.isLoggable(Level.WARNING)) LOG.warning(e.toString());
				return Collections.emptyMap();
			}
		}

		public void setConfig(TransportConfig c) {
			assert id != null;
			try {
				db.setConfig(id, c);
			} catch(DbException e) {
				if(LOG.isLoggable(Level.WARNING)) LOG.warning(e.toString());
			}
		}

		public void setLocalProperties(TransportProperties p) {
			assert id != null;
			if(p.size() > MAX_PROPERTIES_PER_TRANSPORT) {
				if(LOG.isLoggable(Level.WARNING))
					LOG.warning("Plugin " + id + " set too many properties");
				return;
			}
			for(String s : p.keySet()) {
				if(s.length() > MAX_PROPERTY_LENGTH) {
					if(LOG.isLoggable(Level.WARNING))
						LOG.warning("Plugin " + id + " set long key: " + s);
					return;
				}
			}
			for(String s : p.values()) {
				if(s.length() > MAX_PROPERTY_LENGTH) {
					if(LOG.isLoggable(Level.WARNING))
						LOG.warning("Plugin " + id + " set long value: " + s);
					return;
				}
			}
			try {
				db.setLocalProperties(id, p);
			} catch(DbException e) {
				if(LOG.isLoggable(Level.WARNING)) LOG.warning(e.toString());
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

		public void readerCreated(SimplexTransportReader r) {
			assert id != null;
			dispatcher.dispatchReader(id, r);
		}

		public void writerCreated(ContactId c, SimplexTransportWriter w) {
			dispatcher.dispatchWriter(c, id, w);
		}
	}

	private class DuplexCallback extends PluginCallbackImpl
	implements DuplexPluginCallback {

		public void incomingConnectionCreated(DuplexTransportConnection d) {
			assert id != null;
			dispatcher.dispatchIncomingConnection(id, d);
		}

		public void outgoingConnectionCreated(ContactId c,
				DuplexTransportConnection d) {
			dispatcher.dispatchOutgoingConnection(c, id, d);
		}
	}
}