package net.sf.briar.invitation;

import java.util.ArrayList;
import java.util.Collection;
import java.util.concurrent.atomic.AtomicBoolean;

import net.sf.briar.api.crypto.CryptoComponent;
import net.sf.briar.api.crypto.PseudoRandom;
import net.sf.briar.api.invitation.ConnectionCallback;
import net.sf.briar.api.invitation.InvitationManager;
import net.sf.briar.api.plugins.PluginManager;
import net.sf.briar.api.plugins.duplex.DuplexPlugin;

import com.google.inject.Inject;

class InvitationManagerImpl implements InvitationManager {

	private final CryptoComponent crypto;
	private final PluginManager pluginManager;

	@Inject
	InvitationManagerImpl(CryptoComponent crypto, PluginManager pluginManager) {
		this.crypto = crypto;
		this.pluginManager = pluginManager;
	}

	public void connect(int localCode, int remoteCode, ConnectionCallback c) {
		Collection<DuplexPlugin> plugins = pluginManager.getInvitationPlugins();
		// Alice is the party with the smaller invitation code
		if(localCode < remoteCode) {
			startAliceWorkers(plugins, localCode, remoteCode, c);
		} else {
			startBobWorkers(plugins, localCode, remoteCode, c);
		}
	}

	private void startAliceWorkers(Collection<DuplexPlugin> plugins,
			int localCode, int remoteCode, ConnectionCallback c) {
		AtomicBoolean connected = new AtomicBoolean(false);
		AtomicBoolean succeeded = new AtomicBoolean(false);
		Collection<Thread> workers = new ArrayList<Thread>();
		for(DuplexPlugin p : plugins) {
			PseudoRandom r = crypto.getPseudoRandom(localCode, remoteCode);
			Thread worker = new AliceConnector(p, r, c, connected, succeeded);
			workers.add(worker);
			worker.start();
		}
		new FailureNotifier(workers, succeeded, c).start();
	}

	private void startBobWorkers(Collection<DuplexPlugin> plugins,
			int localCode, int remoteCode, ConnectionCallback c) {
		AtomicBoolean connected = new AtomicBoolean(false);
		AtomicBoolean succeeded = new AtomicBoolean(false);
		Collection<Thread> workers = new ArrayList<Thread>();
		for(DuplexPlugin p : plugins) {
			PseudoRandom r = crypto.getPseudoRandom(remoteCode, localCode);
			Thread worker = new BobConnector(p, r, c, connected, succeeded);
			workers.add(worker);
			worker.start();
		}
		new FailureNotifier(workers, succeeded, c).start();
	}
}
