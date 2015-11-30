package org.briarproject.plugins;

import static org.briarproject.api.invitation.InvitationConstants.CONNECTION_TIMEOUT;

import java.io.IOException;
import java.util.Map;

import org.briarproject.api.ContactId;
import org.briarproject.api.TransportConfig;
import org.briarproject.api.TransportProperties;
import org.briarproject.api.crypto.PseudoRandom;
import org.briarproject.api.plugins.duplex.DuplexPluginCallback;
import org.briarproject.api.plugins.duplex.DuplexTransportConnection;

public abstract class DuplexClientTest extends DuplexTest {

	protected ClientCallback callback = null;

	protected void run() throws IOException {
		assert plugin != null;
		// Start the plugin
		System.out.println("Starting plugin");
		if (!plugin.start()) {
			System.out.println("Plugin failed to start");
			return;
		}
		try {
			// Try to connect to the server
			System.out.println("Creating connection");
			DuplexTransportConnection d = plugin.createConnection(contactId);
			if (d == null) {
				System.out.println("Connection failed");
				return;
			} else {
				System.out.println("Connection created");
				receiveChallengeSendResponse(d);
			}
			if (!plugin.supportsInvitations()) {
				System.out.println("Skipping invitation test");
				return;
			}
			// Try to create an invitation connection
			System.out.println("Creating invitation connection");
			PseudoRandom r = getPseudoRandom(123);
			d = plugin.createInvitationConnection(r, CONNECTION_TIMEOUT);
			if (d == null) {
				System.out.println("Connection failed");
				return;
			} else {
				System.out.println("Connection created");
				sendChallengeReceiveResponse(d);
			}
		} finally {
			// Stop the plugin
			System.out.println("Stopping plugin");
			plugin.stop();
		}
	}

	protected static class ClientCallback implements DuplexPluginCallback {

		private TransportConfig config = null;
		private TransportProperties local = null;
		private Map<ContactId, TransportProperties> remote = null;

		public ClientCallback(TransportConfig config, TransportProperties local,
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

		public void incomingConnectionCreated(DuplexTransportConnection d) {}

		public void outgoingConnectionCreated(ContactId contactId,
				DuplexTransportConnection d) {}
	}
}
