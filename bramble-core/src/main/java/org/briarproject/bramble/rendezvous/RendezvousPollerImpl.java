package org.briarproject.bramble.rendezvous;

import org.briarproject.bramble.PoliteExecutor;
import org.briarproject.bramble.api.Pair;
import org.briarproject.bramble.api.contact.PendingContact;
import org.briarproject.bramble.api.contact.PendingContactId;
import org.briarproject.bramble.api.contact.event.PendingContactAddedEvent;
import org.briarproject.bramble.api.contact.event.PendingContactRemovedEvent;
import org.briarproject.bramble.api.crypto.KeyPair;
import org.briarproject.bramble.api.crypto.SecretKey;
import org.briarproject.bramble.api.crypto.TransportCrypto;
import org.briarproject.bramble.api.db.DatabaseComponent;
import org.briarproject.bramble.api.db.DbException;
import org.briarproject.bramble.api.event.Event;
import org.briarproject.bramble.api.event.EventBus;
import org.briarproject.bramble.api.event.EventExecutor;
import org.briarproject.bramble.api.event.EventListener;
import org.briarproject.bramble.api.identity.IdentityManager;
import org.briarproject.bramble.api.lifecycle.IoExecutor;
import org.briarproject.bramble.api.lifecycle.Service;
import org.briarproject.bramble.api.lifecycle.ServiceException;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.plugin.ConnectionHandler;
import org.briarproject.bramble.api.plugin.ConnectionManager;
import org.briarproject.bramble.api.plugin.Plugin;
import org.briarproject.bramble.api.plugin.PluginManager;
import org.briarproject.bramble.api.plugin.TransportConnectionReader;
import org.briarproject.bramble.api.plugin.TransportConnectionWriter;
import org.briarproject.bramble.api.plugin.TransportId;
import org.briarproject.bramble.api.plugin.duplex.DuplexPlugin;
import org.briarproject.bramble.api.plugin.duplex.DuplexTransportConnection;
import org.briarproject.bramble.api.plugin.event.TransportDisabledEvent;
import org.briarproject.bramble.api.plugin.event.TransportEnabledEvent;
import org.briarproject.bramble.api.properties.TransportProperties;
import org.briarproject.bramble.api.rendezvous.KeyMaterialSource;
import org.briarproject.bramble.api.rendezvous.RendezvousCrypto;
import org.briarproject.bramble.api.rendezvous.RendezvousEndpoint;
import org.briarproject.bramble.api.rendezvous.event.RendezvousFailedEvent;
import org.briarproject.bramble.api.system.Clock;
import org.briarproject.bramble.api.system.Scheduler;

import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.Executor;
import java.util.concurrent.ScheduledExecutorService;
import java.util.logging.Logger;

import javax.annotation.Nullable;
import javax.inject.Inject;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.logging.Level.INFO;
import static java.util.logging.Level.WARNING;
import static java.util.logging.Logger.getLogger;
import static org.briarproject.bramble.api.nullsafety.NullSafety.requireNull;
import static org.briarproject.bramble.api.rendezvous.RendezvousConstants.POLLING_INTERVAL_MS;
import static org.briarproject.bramble.api.rendezvous.RendezvousConstants.RENDEZVOUS_TIMEOUT_MS;
import static org.briarproject.bramble.util.IoUtils.tryToClose;
import static org.briarproject.bramble.util.LogUtils.logException;

@NotNullByDefault
class RendezvousPollerImpl implements RendezvousPoller, Service, EventListener {

	private static final Logger LOG =
			getLogger(RendezvousPollerImpl.class.getName());

	private final ScheduledExecutorService scheduler;
	private final DatabaseComponent db;
	private final IdentityManager identityManager;
	private final TransportCrypto transportCrypto;
	private final RendezvousCrypto rendezvousCrypto;
	private final PluginManager pluginManager;
	private final ConnectionManager connectionManager;
	private final EventBus eventBus;
	private final Clock clock;

	// Executor that runs one task at a time
	private final Executor worker;
	// The following fields are only accessed on the worker
	private final Map<TransportId, PluginState> pluginStates = new HashMap<>();
	private final Map<PendingContactId, SecretKey> rendezvousKeys =
			new HashMap<>();
	@Nullable
	private KeyPair handshakeKeyPair = null;

	@Inject
	RendezvousPollerImpl(@IoExecutor Executor ioExecutor,
			@Scheduler ScheduledExecutorService scheduler,
			DatabaseComponent db,
			IdentityManager identityManager,
			TransportCrypto transportCrypto,
			RendezvousCrypto rendezvousCrypto,
			PluginManager pluginManager,
			ConnectionManager connectionManager,
			EventBus eventBus,
			Clock clock) {
		this.scheduler = scheduler;
		this.db = db;
		this.identityManager = identityManager;
		this.transportCrypto = transportCrypto;
		this.rendezvousCrypto = rendezvousCrypto;
		this.pluginManager = pluginManager;
		this.connectionManager = connectionManager;
		this.eventBus = eventBus;
		this.clock = clock;
		worker = new PoliteExecutor("RendezvousPoller", ioExecutor, 1);
	}

	@Override
	public void startService() throws ServiceException {
		try {
			db.transaction(true, txn -> {
				Collection<PendingContact> pending = db.getPendingContacts(txn);
				// Use a commit action to prevent races with add/remove events
				txn.attach(() -> addPendingContactsAsync(pending));
			});
		} catch (DbException e) {
			throw new ServiceException(e);
		}
		scheduler.scheduleAtFixedRate(this::poll, POLLING_INTERVAL_MS,
				POLLING_INTERVAL_MS, MILLISECONDS);
	}

	@EventExecutor
	private void addPendingContactsAsync(Collection<PendingContact> pending) {
		worker.execute(() -> {
			for (PendingContact p : pending) addPendingContact(p);
		});
	}

	// Worker
	private void addPendingContact(PendingContact p) {
		long now = clock.currentTimeMillis();
		long expiry = p.getTimestamp() + RENDEZVOUS_TIMEOUT_MS;
		if (expiry > now) {
			scheduler.schedule(() -> expirePendingContactAsync(p.getId()),
					expiry - now, MILLISECONDS);
		} else {
			eventBus.broadcast(new RendezvousFailedEvent(p.getId()));
			return;
		}
		try {
			if (handshakeKeyPair == null) {
				handshakeKeyPair = db.transactionWithResult(true,
						identityManager::getHandshakeKeys);
			}
			SecretKey staticMasterKey = transportCrypto
					.deriveStaticMasterKey(p.getPublicKey(), handshakeKeyPair);
			SecretKey rendezvousKey = rendezvousCrypto
					.deriveRendezvousKey(staticMasterKey);
			requireNull(rendezvousKeys.put(p.getId(), rendezvousKey));
			for (PluginState ps : pluginStates.values()) {
				RendezvousEndpoint endpoint =
						createEndpoint(ps.plugin, p.getId(), rendezvousKey);
				if (endpoint != null)
					requireNull(ps.endpoints.put(p.getId(), endpoint));
			}
		} catch (DbException | GeneralSecurityException e) {
			logException(LOG, WARNING, e);
		}
	}

	@Scheduler
	private void expirePendingContactAsync(PendingContactId p) {
		worker.execute(() -> expirePendingContact(p));
	}

	// Worker
	private void expirePendingContact(PendingContactId p) {
		if (removePendingContact(p))
			eventBus.broadcast(new RendezvousFailedEvent(p));
	}

	// Worker
	private boolean removePendingContact(PendingContactId p) {
		// We can come here twice if a pending contact fails and is then removed
		if (rendezvousKeys.remove(p) == null) return false;
		for (PluginState state : pluginStates.values()) {
			RendezvousEndpoint endpoint = state.endpoints.remove(p);
			if (endpoint != null) tryToClose(endpoint, LOG, INFO);
		}
		return true;
	}

	@Nullable
	private RendezvousEndpoint createEndpoint(DuplexPlugin plugin,
			PendingContactId p, SecretKey rendezvousKey) {
		TransportId t = plugin.getId();
		KeyMaterialSource k =
				rendezvousCrypto.createKeyMaterialSource(rendezvousKey, t);
		Handler h = new Handler(p, t, true);
		return plugin.createRendezvousEndpoint(k, h);
	}

	@Scheduler
	private void poll() {
		worker.execute(() -> {
			for (PluginState state : pluginStates.values()) poll(state);
		});
	}

	// Worker
	private void poll(PluginState state) {
		List<Pair<TransportProperties, ConnectionHandler>> properties =
				new ArrayList<>();
		for (Entry<PendingContactId, RendezvousEndpoint> e :
				state.endpoints.entrySet()) {
			TransportProperties p =
					e.getValue().getRemoteTransportProperties();
			Handler h = new Handler(e.getKey(), state.plugin.getId(), false);
			properties.add(new Pair<>(p, h));
		}
		state.plugin.poll(properties);
	}

	@Override
	public void stopService() {
	}

	@Override
	public void eventOccurred(Event e) {
		if (e instanceof PendingContactAddedEvent) {
			PendingContactAddedEvent p = (PendingContactAddedEvent) e;
			addPendingContactAsync(p.getPendingContact());
		} else if (e instanceof PendingContactRemovedEvent) {
			PendingContactRemovedEvent p = (PendingContactRemovedEvent) e;
			removePendingContactAsync(p.getId());
		} else if (e instanceof TransportEnabledEvent) {
			TransportEnabledEvent t = (TransportEnabledEvent) e;
			addTransportAsync(t.getTransportId());
		} else if (e instanceof TransportDisabledEvent) {
			TransportDisabledEvent t = (TransportDisabledEvent) e;
			removeTransportAsync(t.getTransportId());
		}
	}

	@EventExecutor
	private void addPendingContactAsync(PendingContact p) {
		worker.execute(() -> addPendingContact(p));
	}

	@EventExecutor
	private void removePendingContactAsync(PendingContactId p) {
		worker.execute(() -> removePendingContact(p));
	}

	@EventExecutor
	private void addTransportAsync(TransportId t) {
		Plugin p = pluginManager.getPlugin(t);
		if (p instanceof DuplexPlugin) {
			DuplexPlugin d = (DuplexPlugin) p;
			if (d.supportsRendezvous())
				worker.execute(() -> addTransport(d));
		}
	}

	// Worker
	private void addTransport(DuplexPlugin plugin) {
		TransportId t = plugin.getId();
		Map<PendingContactId, RendezvousEndpoint> endpoints = new HashMap<>();
		for (Entry<PendingContactId, SecretKey> e : rendezvousKeys.entrySet()) {
			RendezvousEndpoint endpoint =
					createEndpoint(plugin, e.getKey(), e.getValue());
			if (endpoint != null) endpoints.put(e.getKey(), endpoint);
		}
		requireNull(pluginStates.put(t, new PluginState(plugin, endpoints)));
	}

	@EventExecutor
	private void removeTransportAsync(TransportId t) {
		worker.execute(() -> removeTransport(t));
	}

	// Worker
	private void removeTransport(TransportId t) {
		PluginState state = pluginStates.remove(t);
		if (state != null) {
			for (RendezvousEndpoint endpoint : state.endpoints.values()) {
				tryToClose(endpoint, LOG, INFO);
			}
		}
	}

	private static class PluginState {

		private final DuplexPlugin plugin;
		private final Map<PendingContactId, RendezvousEndpoint> endpoints;

		private PluginState(DuplexPlugin plugin,
				Map<PendingContactId, RendezvousEndpoint> endpoints) {
			this.plugin = plugin;
			this.endpoints = endpoints;
		}
	}

	private class Handler implements ConnectionHandler {

		private final PendingContactId pendingContactId;
		private final TransportId transportId;
		private final boolean incoming;

		private Handler(PendingContactId pendingContactId,
				TransportId transportId, boolean incoming) {
			this.pendingContactId = pendingContactId;
			this.transportId = transportId;
			this.incoming = incoming;
		}

		@Override
		public void handleConnection(DuplexTransportConnection c) {
			if (incoming) {
				connectionManager.manageIncomingConnection(pendingContactId,
						transportId, c);
			} else {
				connectionManager.manageOutgoingConnection(pendingContactId,
						transportId, c);
			}
		}

		@Override
		public void handleReader(TransportConnectionReader r) {
			throw new UnsupportedOperationException();
		}

		@Override
		public void handleWriter(TransportConnectionWriter w) {
			throw new UnsupportedOperationException();
		}
	}
}
