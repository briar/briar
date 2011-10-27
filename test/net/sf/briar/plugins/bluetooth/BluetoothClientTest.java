package net.sf.briar.plugins.bluetooth;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import net.sf.briar.api.ContactId;
import net.sf.briar.api.TransportConfig;
import net.sf.briar.api.TransportProperties;
import net.sf.briar.api.plugins.StreamPluginCallback;
import net.sf.briar.api.transport.StreamTransportConnection;

// This is not a JUnit test - it has to be run manually while the server test
// is running on another machine
public class BluetoothClientTest extends BluetoothTest {

	private final String serverAddress;

	BluetoothClientTest(String serverAddress) {
		this.serverAddress = serverAddress;
	}

	void run() throws IOException {
		ContactId contactId = new ContactId(0);
		ClientCallback callback = new ClientCallback();
		// Store the server's Bluetooth address and UUID
		TransportProperties p = new TransportProperties();
		p.put("address", serverAddress);
		p.put("uuid", BluetoothServerTest.UUID);
		callback.remote.put(contactId, p);
		// Create the plugin
		Executor e = Executors.newCachedThreadPool();
		BluetoothPlugin plugin = new BluetoothPlugin(e, callback, 0L);
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
			receiveChallengeAndSendResponse(s);
		}
		// Try to send an invitation
		System.out.println("Sending invitation");
		s = plugin.sendInvitation(123, INVITATION_TIMEOUT);
		if(s == null) {
			System.out.println("Connection failed");
		} else {
			System.out.println("Connection created");
			receiveChallengeAndSendResponse(s);
		}
		// Try to accept an invitation
		System.out.println("Accepting invitation");
		s = plugin.acceptInvitation(456, INVITATION_TIMEOUT);
		if(s == null) {
			System.out.println("Connection failed");
		} else {
			System.out.println("Connection created");
			sendChallengeAndReceiveResponse(s);
		}
		// Stop the plugin
		System.out.println("Stopping plugin");
		plugin.stop();
	}

	public static void main(String[] args) throws Exception {
		if(args.length != 1) {
			System.err.println("Please specify the server's Bluetooth address");
			System.exit(1);
		}
		new BluetoothClientTest(args[0]).run();
	}

	private static class ClientCallback implements StreamPluginCallback {

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

		public void incomingConnectionCreated(StreamTransportConnection c) {}

		public void outgoingConnectionCreated(ContactId contactId,
				StreamTransportConnection c) {}
	}
}
