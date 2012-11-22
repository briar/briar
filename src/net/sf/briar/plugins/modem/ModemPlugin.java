package net.sf.briar.plugins.modem;

import java.io.IOException;
import java.util.Collection;
import java.util.concurrent.Executor;
import java.util.logging.Logger;

import net.sf.briar.api.ContactId;
import net.sf.briar.api.crypto.PseudoRandom;
import net.sf.briar.api.plugins.PluginCallback;
import net.sf.briar.api.plugins.PluginExecutor;
import net.sf.briar.api.plugins.duplex.DuplexPlugin;
import net.sf.briar.api.plugins.duplex.DuplexTransportConnection;
import net.sf.briar.api.protocol.TransportId;
import net.sf.briar.util.StringUtils;

class ModemPlugin implements DuplexPlugin {

	private static final byte[] TRANSPORT_ID =
			StringUtils.fromHexString("8f573867bedf54884b5868ee5d902832" +
					"ee5e522da84d0d431712bd672fbd2f79" +
					"262d27b93879b94ee9afbb80e7fc87fb");
	private static final TransportId ID = new TransportId(TRANSPORT_ID);
	private static final Logger LOG =
			Logger.getLogger(ModemPlugin.class.getName());

	private final Executor pluginExecutor;
	private final PluginCallback callback;
	private final long pollingInterval;

	ModemPlugin(@PluginExecutor Executor pluginExecutor,
			PluginCallback callback, long pollingInterval) {
		this.pluginExecutor = pluginExecutor;
		this.callback = callback;
		this.pollingInterval = pollingInterval;
	}

	public TransportId getId() {
		return ID;
	}

	public String getName() {
		return "MODEM_PLUGIN_NAME";
	}

	public void start() throws IOException {
		// FIXME
	}

	public void stop() throws IOException {
		// FIXME
	}

	public boolean shouldPoll() {
		return true;
	}

	public long getPollingInterval() {
		return pollingInterval;
	}

	public void poll(Collection<ContactId> connected) {
		// FIXME
	}

	public DuplexTransportConnection createConnection(ContactId c) {
		// FIXME
		return null;
	}

	public boolean supportsInvitations() {
		return false;
	}

	public DuplexTransportConnection sendInvitation(PseudoRandom r,
			long timeout) {
		throw new UnsupportedOperationException();
	}

	public DuplexTransportConnection acceptInvitation(PseudoRandom r,
			long timeout) {
		throw new UnsupportedOperationException();
	}
}
