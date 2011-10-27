package net.sf.briar.plugins.bluetooth;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import net.sf.briar.api.ContactId;
import net.sf.briar.api.TransportConfig;
import net.sf.briar.api.TransportProperties;
import net.sf.briar.api.plugins.StreamPluginCallback;
import net.sf.briar.api.transport.StreamTransportConnection;

//This is not a JUnit test - it has to be run manually while the server test
//is running on another machine
public class BluetoothServerTest extends BluetoothTest {

	void run() throws Exception {
		ServerCallback callback = new ServerCallback();
		// Store the UUID
		callback.config.put("uuid", UUID);
		// Create the plugin
		Executor e = Executors.newCachedThreadPool();
		BluetoothPlugin plugin = new BluetoothPlugin(e, callback, 0L);
		// Start the plugin
		System.out.println("Starting plugin");
		plugin.start();
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
			sendChallengeAndReceiveResponse(s);
		}
		// Try to send an invitation
		System.out.println("Sending invitation");
		s = plugin.sendInvitation(456, INVITATION_TIMEOUT);
		if(s == null) {
			System.out.println("Connection failed");
		} else {
			System.out.println("Connection created");
			receiveChallengeAndSendResponse(s);
		}
		// Stop the plugin
		System.out.println("Stopping plugin");
		plugin.stop();
	}

	public static void main(String[] args) throws Exception {
		new BluetoothServerTest().run();
	}

	private class ServerCallback implements StreamPluginCallback {

		private TransportConfig config = new TransportConfig();
		private TransportProperties local = new TransportProperties();
		private Map<ContactId, TransportProperties> remote =
			new HashMap<ContactId, TransportProperties>();

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
			sendChallengeAndReceiveResponse(s);
			synchronized(this) {
				notifyAll();
			}
		}

		public void outgoingConnectionCreated(ContactId contactId,
				StreamTransportConnection c) {}
	}
}
