package net.sf.briar.plugins;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.logging.Level;
import java.util.logging.Logger;

import net.sf.briar.api.ContactId;
import net.sf.briar.api.TransportConfig;
import net.sf.briar.api.TransportId;
import net.sf.briar.api.TransportProperties;
import net.sf.briar.api.db.DatabaseComponent;
import net.sf.briar.api.db.DbException;
import net.sf.briar.api.plugins.BatchPlugin;
import net.sf.briar.api.plugins.BatchPluginCallback;
import net.sf.briar.api.plugins.BatchPluginFactory;
import net.sf.briar.api.plugins.Plugin;
import net.sf.briar.api.plugins.PluginCallback;
import net.sf.briar.api.plugins.PluginManager;
import net.sf.briar.api.plugins.StreamPlugin;
import net.sf.briar.api.plugins.StreamPluginCallback;
import net.sf.briar.api.plugins.StreamPluginFactory;
import net.sf.briar.api.protocol.TransportUpdate;
import net.sf.briar.api.transport.BatchTransportReader;
import net.sf.briar.api.transport.BatchTransportWriter;
import net.sf.briar.api.transport.ConnectionDispatcher;
import net.sf.briar.api.transport.StreamTransportConnection;
import net.sf.briar.api.ui.UiCallback;

import com.google.inject.Inject;

class PluginManagerImpl implements PluginManager {

	private static final Logger LOG =
		Logger.getLogger(PluginManagerImpl.class.getName());

	private static final String[] BATCH_FACTORIES = new String[] {
		"net.sf.briar.plugins.file.RemovableDrivePluginFactory"
	};

	private static final String[] STREAM_FACTORIES = new String[] {
		"net.sf.briar.plugins.bluetooth.BluetoothPluginFactory",
		"net.sf.briar.plugins.socket.SimpleSocketPluginFactory"
	};

	private final Executor executor;
	private final DatabaseComponent db;
	private final Poller poller;
	private final ConnectionDispatcher dispatcher;
	private final UiCallback uiCallback;
	private final List<BatchPlugin> batchPlugins;
	private final List<StreamPlugin> streamPlugins;

	@Inject
	PluginManagerImpl(Executor executor, DatabaseComponent db, Poller poller,
			ConnectionDispatcher dispatcher, UiCallback uiCallback) {
		this.executor = executor;
		this.db = db;
		this.poller = poller;
		this.dispatcher = dispatcher;
		this.uiCallback = uiCallback;
		batchPlugins = new ArrayList<BatchPlugin>();
		streamPlugins = new ArrayList<StreamPlugin>();
	}

	public synchronized int startPlugins() {
		Set<TransportId> ids = new HashSet<TransportId>();
		// Instantiate and start the batch plugins
		for(String s : BATCH_FACTORIES) {
			try {
				Class<?> c = Class.forName(s);
				BatchPluginFactory factory =
					(BatchPluginFactory) c.newInstance();
				BatchCallback callback = new BatchCallback();
				BatchPlugin plugin = factory.createPlugin(executor, callback);
				if(plugin == null) {
					if(LOG.isLoggable(Level.INFO))
						LOG.info(factory.getClass().getSimpleName() +
						" did not create a plugin");
					continue;
				}
				TransportId id = plugin.getId();
				if(!ids.add(id)) {
					if(LOG.isLoggable(Level.WARNING))
						LOG.warning("Duplicate transport ID: " + id);
					continue;
				}
				callback.setId(id);
				plugin.start();
				batchPlugins.add(plugin);
			} catch(ClassCastException e) {
				if(LOG.isLoggable(Level.WARNING)) LOG.warning(e.getMessage());
				continue;
			} catch(Exception e) {
				if(LOG.isLoggable(Level.WARNING)) LOG.warning(e.getMessage());
				continue;
			}
		}
		// Instantiate and start the stream plugins
		for(String s : STREAM_FACTORIES) {
			try {
				Class<?> c = Class.forName(s);
				StreamPluginFactory factory =
					(StreamPluginFactory) c.newInstance();
				StreamCallback callback = new StreamCallback();
				StreamPlugin plugin = factory.createPlugin(executor, callback);
				if(plugin == null) {
					if(LOG.isLoggable(Level.INFO))
						LOG.info(factory.getClass().getSimpleName() +
						" did not create a plugin");
					continue;
				}
				TransportId id = plugin.getId();
				if(!ids.add(id)) {
					if(LOG.isLoggable(Level.WARNING))
						LOG.warning("Duplicate transport ID: " + id);
					continue;
				}
				callback.setId(id);
				plugin.start();
				streamPlugins.add(plugin);
			} catch(ClassCastException e) {
				if(LOG.isLoggable(Level.WARNING)) LOG.warning(e.getMessage());
				continue;
			} catch(Exception e) {
				if(LOG.isLoggable(Level.WARNING)) LOG.warning(e.getMessage());
				continue;
			}
		}
		// Start the poller
		List<Plugin> plugins = new ArrayList<Plugin>();
		plugins.addAll(batchPlugins);
		plugins.addAll(streamPlugins);
		poller.startPolling(plugins);
		// Return the number of plugins started
		return batchPlugins.size() + streamPlugins.size();
	}

	public synchronized int stopPlugins() {
		int stopped = 0;
		// Stop the batch plugins
		for(BatchPlugin plugin : batchPlugins) {
			try {
				plugin.stop();
				stopped++;
			} catch(IOException e) {
				if(LOG.isLoggable(Level.WARNING)) LOG.warning(e.getMessage());
			}
		}
		batchPlugins.clear();
		// Stop the stream plugins
		for(StreamPlugin plugin : streamPlugins) {
			try {
				plugin.stop();
				stopped++;
			} catch(IOException e) {
				if(LOG.isLoggable(Level.WARNING)) LOG.warning(e.getMessage());
			}
		}
		streamPlugins.clear();
		// Return the number of plugins stopped
		return stopped;
	}

	private abstract class PluginCallbackImpl implements PluginCallback {

		protected volatile TransportId id = null;

		protected void setId(TransportId id) {
			assert this.id == null;
			this.id = id;
		}

		public TransportConfig getConfig() {
			assert id != null;
			try {
				return db.getConfig(id);
			} catch(DbException e) {
				if(LOG.isLoggable(Level.WARNING)) LOG.warning(e.getMessage());
				return new TransportConfig();
			}
		}

		public TransportProperties getLocalProperties() {
			assert id != null;
			try {
				TransportProperties p = db.getLocalProperties(id);
				return p == null ? new TransportProperties() : p;
			} catch(DbException e) {
				if(LOG.isLoggable(Level.WARNING)) LOG.warning(e.getMessage());
				return new TransportProperties();
			}
		}

		public Map<ContactId, TransportProperties> getRemoteProperties() {
			assert id != null;
			try {
				return db.getRemoteProperties(id);
			} catch(DbException e) {
				if(LOG.isLoggable(Level.WARNING)) LOG.warning(e.getMessage());
				return Collections.emptyMap();
			}
		}

		public void setConfig(TransportConfig c) {
			assert id != null;
			try {
				db.setConfig(id, c);
			} catch(DbException e) {
				if(LOG.isLoggable(Level.WARNING)) LOG.warning(e.getMessage());
			}
		}

		public void setLocalProperties(TransportProperties p) {
			assert id != null;
			if(p.size() > TransportUpdate.MAX_PROPERTIES_PER_PLUGIN) {
				if(LOG.isLoggable(Level.WARNING))
					LOG.warning("Plugin " + id + " set too many properties");
				return;
			}
			for(String s : p.keySet()) {
				if(s.length() > TransportUpdate.MAX_KEY_OR_VALUE_LENGTH) {
					if(LOG.isLoggable(Level.WARNING))
						LOG.warning("Plugin " + id + " set long key: " + s);
					return;
				}
			}
			for(String s : p.values()) {
				if(s.length() > TransportUpdate.MAX_KEY_OR_VALUE_LENGTH) {
					if(LOG.isLoggable(Level.WARNING))
						LOG.warning("Plugin " + id + " set long value: " + s);
					return;
				}
			}
			try {
				db.setLocalProperties(id, p);
			} catch(DbException e) {
				if(LOG.isLoggable(Level.WARNING)) LOG.warning(e.getMessage());
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

	private class BatchCallback extends PluginCallbackImpl
	implements BatchPluginCallback {

		public void readerCreated(BatchTransportReader r) {
			assert id != null;
			dispatcher.dispatchReader(id, r);
		}

		public void writerCreated(ContactId c, BatchTransportWriter w) {
			assert id != null;
			dispatcher.dispatchWriter(id, c, w);
		}
	}

	private class StreamCallback extends PluginCallbackImpl
	implements StreamPluginCallback {

		public void incomingConnectionCreated(StreamTransportConnection s) {
			assert id != null;
			dispatcher.dispatchIncomingConnection(id, s);
		}

		public void outgoingConnectionCreated(ContactId c,
				StreamTransportConnection s) {
			assert id != null;
			dispatcher.dispatchOutgoingConnection(id, c, s);
		}
	}
}