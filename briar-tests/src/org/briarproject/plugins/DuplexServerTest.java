package org.briarproject.plugins;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.briarproject.api.invitation.InvitationConstants.CONNECTION_TIMEOUT;

import java.util.Map;
import java.util.concurrent.CountDownLatch;

import org.briarproject.api.ContactId;
import org.briarproject.api.TransportConfig;
import org.briarproject.api.TransportProperties;
import org.briarproject.api.plugins.duplex.DuplexPluginCallback;
import org.briarproject.api.plugins.duplex.DuplexTransportConnection;

public abstract class DuplexServerTest extends DuplexTest {

	protected ServerCallback callback = null;

	protected void run() throws Exception {
		assert callback != null;
		assert plugin != null;
		// Start the plugin
		System.out.println("Starting plugin");
		if(!plugin.start()) {
			System.out.println("Plugin failed to start");
			return;
		}
		try {
			// Wait for a connection
			System.out.println("Waiting for connection");
			if(!callback.latch.await(120, SECONDS)) {
				System.out.println("No connection received");
				return;
			}
			if(!plugin.supportsInvitations()) {
				System.out.println("Skipping invitation test");
				return;
			}
			// Try to create an invitation connection 
			System.out.println("Creating invitation connection");
			DuplexTransportConnection d = plugin.createInvitationConnection(
					getPseudoRandom(123), CONNECTION_TIMEOUT);
			if(d == null) {
				System.out.println("Connection failed");
				return;
			} else {
				System.out.println("Connection created");
				receiveChallengeSendResponse(d);
			}
		} finally {
			// Stop the plugin
			System.out.println("Stopping plugin");
			plugin.stop();
		}
	}

	protected class ServerCallback implements DuplexPluginCallback {

		private final CountDownLatch latch = new CountDownLatch(1);

		private TransportConfig config;
		private TransportProperties local;
		private Map<ContactId, TransportProperties> remote;

		public ServerCallback(TransportConfig config, TransportProperties local,
				Map<ContactId, TransportProperties> remote) {
			this.config = config;
			this.local = local;
			this.remote = remote;
		}

		public TransportConfig getConfig() {
			return config;
		}

		public TransportProperties getLocalProperties() {
			return local;
		}

		public Map<ContactId, TransportProperties> getRemoteProperties() {
			return remote;
		}

		public void mergeConfig(TransportConfig c) {
			config = c;
		}

		public void mergeLocalProperties(TransportProperties p) {
			local = p;
		}

		public int showChoice(String[] options, String... message) {
			return -1;
		}

		public boolean showConfirmationMessage(String... message) {
			return false;
		}

		public void showMessage(String... message) {}

		public void pollNow() {}

		public void incomingConnectionCreated(DuplexTransportConnection d) {
			System.out.println("Connection received");
			sendChallengeReceiveResponse(d);
			latch.countDown();
		}

		public void outgoingConnectionCreated(ContactId c,
				DuplexTransportConnection d) {}
	}
}
