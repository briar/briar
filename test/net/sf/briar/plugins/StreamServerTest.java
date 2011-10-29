package net.sf.briar.plugins;

import java.util.Map;

import net.sf.briar.api.ContactId;
import net.sf.briar.api.TransportConfig;
import net.sf.briar.api.TransportProperties;
import net.sf.briar.api.plugins.StreamPluginCallback;
import net.sf.briar.api.transport.StreamTransportConnection;

public abstract class StreamServerTest extends StreamTest {

	protected void run() throws Exception {
		assert callback != null;
		assert plugin != null;
		// Start the plugin
		System.out.println("Starting plugin");
		plugin.start();
		// Print the local transport properties
		System.out.println("Local transport properties:");
		System.out.println(callback.getLocalProperties());
		// Wait for a connection
		System.out.println("Waiting for connection");
		synchronized(callback) {
			callback.wait();
		}
		// Try to accept an invitation
		System.out.println("Accepting invitation");
		StreamTransportConnection s = plugin.acceptInvitation(123,
				INVITATION_TIMEOUT);
		if(s == null) {
			System.out.println("Connection failed");
		} else {
			System.out.println("Connection created");
			sendChallengeReceiveResponse(s);
		}
		// Try to send an invitation
		System.out.println("Sending invitation");
		s = plugin.sendInvitation(456, INVITATION_TIMEOUT);
		if(s == null) {
			System.out.println("Connection failed");
		} else {
			System.out.println("Connection created");
			receiveChallengeSendResponse(s);
		}
		// Stop the plugin
		System.out.println("Stopping plugin");
		plugin.stop();
	}

	protected class ServerCallback implements StreamPluginCallback {

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

		public void incomingConnectionCreated(StreamTransportConnection s) {
			System.out.println("Connection received");
			sendChallengeReceiveResponse(s);
			synchronized(this) {
				notifyAll();
			}
		}

		public void outgoingConnectionCreated(ContactId contactId,
				StreamTransportConnection c) {}
	}
}
