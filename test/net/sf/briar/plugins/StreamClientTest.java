package net.sf.briar.plugins;

import java.io.IOException;
import java.util.Map;

import net.sf.briar.api.ContactId;
import net.sf.briar.api.TransportConfig;
import net.sf.briar.api.TransportProperties;
import net.sf.briar.api.plugins.StreamPluginCallback;
import net.sf.briar.api.transport.StreamTransportConnection;

public abstract class StreamClientTest extends StreamTest {

	protected ClientCallback callback = null;

	protected void run() throws IOException {
		assert plugin != null;
		// Start the plugin
		System.out.println("Starting plugin");
		plugin.start();
		// Try to connect to the server
		System.out.println("Creating connection");
		StreamTransportConnection s = plugin.createConnection(contactId);
		if(s == null) {
			System.out.println("Connection failed");
		} else {
			System.out.println("Connection created");
			receiveChallengeSendResponse(s);
		}
		// Try to send an invitation
		System.out.println("Sending invitation");
		s = plugin.sendInvitation(123, INVITATION_TIMEOUT);
		if(s == null) {
			System.out.println("Connection failed");
		} else {
			System.out.println("Connection created");
			receiveChallengeSendResponse(s);
		}
		// Try to accept an invitation
		System.out.println("Accepting invitation");
		s = plugin.acceptInvitation(456, INVITATION_TIMEOUT);
		if(s == null) {
			System.out.println("Connection failed");
		} else {
			System.out.println("Connection created");
			sendChallengeReceiveResponse(s);
		}
		// Stop the plugin
		System.out.println("Stopping plugin");
		plugin.stop();
	}

	protected static class ClientCallback implements StreamPluginCallback {

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

		public void incomingConnectionCreated(StreamTransportConnection c) {}

		public void outgoingConnectionCreated(ContactId contactId,
				StreamTransportConnection c) {}
	}
}
