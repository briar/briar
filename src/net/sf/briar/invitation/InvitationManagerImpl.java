package net.sf.briar.invitation;

import java.util.Collection;

import net.sf.briar.api.crypto.CryptoComponent;
import net.sf.briar.api.crypto.PseudoRandom;
import net.sf.briar.api.invitation.ConfirmationCallback;
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
			PseudoRandom r = crypto.getPseudoRandom(localCode, remoteCode);
			startAliceInvitationWorker(plugins, r, c);
		} else {
			PseudoRandom r = crypto.getPseudoRandom(remoteCode, localCode);
			startBobInvitationWorker(plugins, r, c);
		}
	}

	private void startAliceInvitationWorker(Collection<DuplexPlugin> plugins,
			PseudoRandom r, ConnectionCallback c) {
		// FIXME
		new FakeWorkerThread(c).start();
	}

	private void startBobInvitationWorker(Collection<DuplexPlugin> plugins,
			PseudoRandom r, ConnectionCallback c) {
		// FIXME
		new FakeWorkerThread(c).start();
	}

	private static class FakeWorkerThread extends Thread {

		private final ConnectionCallback callback;

		private FakeWorkerThread(ConnectionCallback callback) {
			this.callback = callback;
		}

		@Override
		public void run() {
			try {
				Thread.sleep((long) (Math.random() * 30 * 1000));
			} catch(InterruptedException ignored) {}
			if(Math.random() < 0.8) {
				callback.connectionNotEstablished();
			} else {
				callback.connectionEstablished(123456, 123456,
						new ConfirmationCallback() {

					public void codesMatch() {}

					public void codesDoNotMatch() {}
				});
				try {
					Thread.sleep((long) (Math.random() * 10 * 1000));
				} catch(InterruptedException ignored) {}
				if(Math.random() < 0.5) callback.codesMatch();
				else callback.codesDoNotMatch();
			}
		}
	}
}
