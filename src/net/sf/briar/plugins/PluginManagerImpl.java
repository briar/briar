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
import net.sf.briar.api.TransportProperties;
import net.sf.briar.api.android.AndroidExecutor;
import net.sf.briar.api.db.DatabaseComponent;
import net.sf.briar.api.db.DbException;
import net.sf.briar.api.lifecycle.ShutdownManager;
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
import net.sf.briar.util.OsUtils;
import android.content.Context;

import com.google.inject.Inject;

class PluginManagerImpl implements PluginManager {

	private static final Logger LOG =
			Logger.getLogger(PluginManagerImpl.class.getName());

	private static final String[] ANDROID_SIMPLEX_FACTORIES = new String[0];

	private static final String[] ANDROID_DUPLEX_FACTORIES = new String[] {
		"net.sf.briar.plugins.droidtooth.DroidtoothPluginFactory",
		"net.sf.briar.plugins.tcp.LanTcpPluginFactory",
		"net.sf.briar.plugins.tcp.WanTcpPluginFactory",
		"net.sf.briar.plugins.tor.TorPluginFactory"
	};

	private static final String[] J2SE_SIMPLEX_FACTORIES = new String[] {
		"net.sf.briar.plugins.file.RemovableDrivePluginFactory"
	};

	private static final String[] J2SE_DUPLEX_FACTORIES = new String[] {
		"net.sf.briar.plugins.bluetooth.BluetoothPluginFactory",
		"net.sf.briar.plugins.modem.ModemPluginFactory",
		"net.sf.briar.plugins.tcp.LanTcpPluginFactory",
		"net.sf.briar.plugins.tcp.WanTcpPluginFactory",
		"net.sf.briar.plugins.tor.TorPluginFactory"
	};

	private final ExecutorService pluginExecutor;
	private final AndroidExecutor androidExecutor;
	private final ShutdownManager shutdownManager;
	private final DatabaseComponent db;
	private final Poller poller;
	private final ConnectionDispatcher dispatcher;
	private final UiCallback uiCallback;
	private final List<SimplexPlugin> simplexPlugins; // Locking: this
	private final List<DuplexPlugin> duplexPlugins; // Locking: this

	@Inject
	PluginManagerImpl(@PluginExecutor ExecutorService pluginExecutor,
			AndroidExecutor androidExecutor, ShutdownManager shutdownManager,
			DatabaseComponent db, Poller poller,
			ConnectionDispatcher dispatcher, UiCallback uiCallback) {
		this.pluginExecutor = pluginExecutor;
		this.androidExecutor = androidExecutor;
		this.shutdownManager = shutdownManager;
		this.db = db;
		this.poller = poller;
		this.dispatcher = dispatcher;
		this.uiCallback = uiCallback;
		simplexPlugins = new ArrayList<SimplexPlugin>();
		duplexPlugins = new ArrayList<DuplexPlugin>();
	}

	public synchronized int start(Context appContext) {
		Set<TransportId> ids = new HashSet<TransportId>();
		// Instantiate and start the simplex plugins
		if(LOG.isLoggable(INFO)) LOG.info("Starting simplex plugins");
		for(String s : getSimplexPluginFactoryNames()) {
			try {
				Class<?> c = Class.forName(s);
				SimplexPluginFactory factory =
						(SimplexPluginFactory) c.newInstance();
				SimplexCallback callback = new SimplexCallback();
				SimplexPlugin plugin = factory.createPlugin(pluginExecutor,
						androidExecutor, appContext, shutdownManager, callback);
				if(plugin == null) {
					if(LOG.isLoggable(INFO)) {
						LOG.info(factory.getClass().getSimpleName()
								+ " did not create a plugin");
					}
					continue;
				}
				TransportId id = plugin.getId();
				if(!ids.add(id)) {
					if(LOG.isLoggable(WARNING))
						LOG.warning("Duplicate transport ID: " + id);
					continue;
				}
				callback.init(id);
				plugin.start();
				simplexPlugins.add(plugin);
			} catch(ClassCastException e) {
				if(LOG.isLoggable(WARNING)) LOG.warning(e.toString());
				continue;
			} catch(Exception e) {
				if(LOG.isLoggable(WARNING)) LOG.warning(e.toString());
				continue;
			}
		}
		// Instantiate and start the duplex plugins
		if(LOG.isLoggable(INFO)) LOG.info("Starting duplex plugins");
		for(String s : getDuplexPluginFactoryNames()) {
			try {
				Class<?> c = Class.forName(s);
				DuplexPluginFactory factory =
						(DuplexPluginFactory) c.newInstance();
				DuplexCallback callback = new DuplexCallback();
				DuplexPlugin plugin = factory.createPlugin(pluginExecutor,
						androidExecutor, appContext, shutdownManager, callback);
				if(plugin == null) {
					if(LOG.isLoggable(INFO)) {
						LOG.info(factory.getClass().getSimpleName()
								+ " did not create a plugin");
					}
					continue;
				}
				TransportId id = plugin.getId();
				if(!ids.add(id)) {
					if(LOG.isLoggable(WARNING))
						LOG.warning("Duplicate transport ID: " + id);
					continue;
				}
				callback.init(id);
				plugin.start();
				duplexPlugins.add(plugin);
			} catch(ClassCastException e) {
				if(LOG.isLoggable(WARNING)) LOG.warning(e.toString());
				continue;
			} catch(Exception e) {
				if(LOG.isLoggable(WARNING)) LOG.warning(e.toString());
				continue;
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

	private String[] getSimplexPluginFactoryNames() {
		if(OsUtils.isAndroid()) return ANDROID_SIMPLEX_FACTORIES;
		return J2SE_SIMPLEX_FACTORIES;
	}

	private String[] getDuplexPluginFactoryNames() {
		if(OsUtils.isAndroid()) return ANDROID_DUPLEX_FACTORIES;
		return J2SE_DUPLEX_FACTORIES;
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
				if(LOG.isLoggable(WARNING)) LOG.warning(e.toString());
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
				if(LOG.isLoggable(WARNING)) LOG.warning(e.toString());
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
				if(LOG.isLoggable(WARNING)) LOG.warning(e.toString());
				return new TransportConfig();
			}
		}

		public TransportProperties getLocalProperties() {
			assert id != null;
			try {
				TransportProperties p = db.getLocalProperties(id);
				return p == null ? new TransportProperties() : p;
			} catch(DbException e) {
				if(LOG.isLoggable(WARNING)) LOG.warning(e.toString());
				return new TransportProperties();
			}
		}

		public Map<ContactId, TransportProperties> getRemoteProperties() {
			assert id != null;
			try {
				return db.getRemoteProperties(id);
			} catch(DbException e) {
				if(LOG.isLoggable(WARNING)) LOG.warning(e.toString());
				return Collections.emptyMap();
			}
		}

		public void mergeConfig(TransportConfig c) {
			assert id != null;
			try {
				db.mergeConfig(id, c);
			} catch(DbException e) {
				if(LOG.isLoggable(WARNING)) LOG.warning(e.toString());
			}
		}

		public void mergeLocalProperties(TransportProperties p) {
			assert id != null;
			try {
				db.mergeLocalProperties(id, p);
			} catch(DbException e) {
				if(LOG.isLoggable(WARNING)) LOG.warning(e.toString());
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