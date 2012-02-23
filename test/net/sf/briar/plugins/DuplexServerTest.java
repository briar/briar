package net.sf.briar.plugins;

import java.util.Map;
import java.util.concurrent.CountDownLatch;

import net.sf.briar.api.ContactId;
import net.sf.briar.api.TransportConfig;
import net.sf.briar.api.TransportProperties;
import net.sf.briar.api.plugins.duplex.DuplexPluginCallback;
import net.sf.briar.api.plugins.duplex.DuplexTransportConnection;

public abstract class DuplexServerTest extends DuplexTest {

	protected ServerCallback callback = null;

	protected void run() throws Exception {
		assert callback != null;
		assert plugin != null;
		// Start the plugin
		System.out.println("Starting plugin");
		plugin.start();
		// Wait for a connection
		System.out.println("Waiting for connection");
		callback.latch.await();
		// Try to accept an invitation
		System.out.println("Accepting invitation");
		DuplexTransportConnection d = plugin.acceptInvitation(
				getPseudoRandom(123), INVITATION_TIMEOUT);
		if(d == null) {
			System.out.println("Connection failed");
		} else {
			System.out.println("Connection created");
			sendChallengeReceiveResponse(d);
		}
		// Try to send an invitation
		System.out.println("Sending invitation");
		d = plugin.sendInvitation(getPseudoRandom(456), INVITATION_TIMEOUT);
		if(d == null) {
			System.out.println("Connection failed");
		} else {
			System.out.println("Connection created");
			receiveChallengeSendResponse(d);
		}
		// Stop the plugin
		System.out.println("Stopping plugin");
		plugin.stop();
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

		public void setConfig(TransportConfig c) {
			config = c;
		}

		public void setLocalProperties(TransportProperties p) {
			local = p;
		}

		public int showChoice(String[] options, String... message) {
			return -1;
		}

		public boolean showConfirmationMessage(String... message) {
			return false;
		}

		public void showMessage(String... message) {}

		public void incomingConnectionCreated(DuplexTransportConnection d) {
			System.out.println("Connection received");
			sendChallengeReceiveResponse(d);
			latch.countDown();
		}

		public void outgoingConnectionCreated(ContactId c,
				DuplexTransportConnection d) {}
	}
}
