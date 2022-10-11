package org.briarproject.bramble.test;

import org.briarproject.bramble.api.Pair;
import org.briarproject.bramble.api.data.BdfList;
import org.briarproject.bramble.api.keyagreement.KeyAgreementListener;
import org.briarproject.bramble.api.plugin.ConnectionHandler;
import org.briarproject.bramble.api.plugin.PluginCallback;
import org.briarproject.bramble.api.plugin.TorConstants;
import org.briarproject.bramble.api.plugin.TransportId;
import org.briarproject.bramble.api.plugin.duplex.DuplexPlugin;
import org.briarproject.bramble.api.plugin.duplex.DuplexTransportConnection;
import org.briarproject.bramble.api.properties.TransportProperties;
import org.briarproject.bramble.api.rendezvous.KeyMaterialSource;
import org.briarproject.bramble.api.rendezvous.RendezvousEndpoint;
import org.briarproject.nullsafety.NotNullByDefault;

import java.util.Collection;
import java.util.logging.Logger;

import javax.annotation.Nullable;

import static java.util.logging.Logger.getLogger;
import static org.briarproject.bramble.api.plugin.Plugin.State.ACTIVE;
import static org.briarproject.bramble.api.plugin.Plugin.State.INACTIVE;

@NotNullByDefault
public class FakeTorPlugin implements DuplexPlugin {

	private static final Logger LOG =
			getLogger(FakeTorPlugin.class.getName());
	private final PluginCallback callback;

	private State state = INACTIVE;

	FakeTorPlugin(PluginCallback callback) {
		this.callback = callback;
	}

	@Override
	public TransportId getId() {
		return TorConstants.ID;
	}

	@Override
	public long getMaxLatency() {
		return 0;
	}

	@Override
	public int getMaxIdleTime() {
		return 0;
	}

	@Override
	public void start() {
		LOG.info("Starting plugin");
		state = ACTIVE;
		callback.pluginStateChanged(state);
	}

	@Override
	public void stop() {
		LOG.info("Stopping plugin");
		state = INACTIVE;
		callback.pluginStateChanged(state);
	}

	@Override
	public State getState() {
		return state;
	}

	@Override
	public int getReasonsDisabled() {
		return 0;
	}

	@Override
	public boolean shouldPoll() {
		return false;
	}

	@Override
	public int getPollingInterval() {
		return 0;
	}

	@Override
	public void poll(
			Collection<Pair<TransportProperties, ConnectionHandler>> properties) {
		// no-op
	}

	@Nullable
	@Override
	public DuplexTransportConnection createConnection(TransportProperties p) {
		return null;
	}

	@Override
	public boolean supportsKeyAgreement() {
		return false;
	}

	@Nullable
	@Override
	public KeyAgreementListener createKeyAgreementListener(
			byte[] localCommitment) {
		return null;
	}

	@Nullable
	@Override
	public DuplexTransportConnection createKeyAgreementConnection(
			byte[] remoteCommitment, BdfList descriptor) {
		return null;
	}

	@Override
	public boolean supportsRendezvous() {
		return false;
	}

	@Nullable
	@Override
	public RendezvousEndpoint createRendezvousEndpoint(KeyMaterialSource k,
			boolean alice, ConnectionHandler incoming) {
		return null;
	}
}
