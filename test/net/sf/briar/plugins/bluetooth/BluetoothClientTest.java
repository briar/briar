package net.sf.briar.plugins.bluetooth;

import java.io.PrintStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Scanner;

import net.sf.briar.api.ContactId;
import net.sf.briar.api.TransportConfig;
import net.sf.briar.api.TransportProperties;
import net.sf.briar.api.plugins.StreamPluginCallback;
import net.sf.briar.api.transport.StreamTransportConnection;
import net.sf.briar.plugins.ImmediateExecutor;

// This is not a JUnit test - it has to be run manually while the server test
// is running on another machine
public class BluetoothClientTest {

	public static final String RESPONSE = "Carrots!";

	public static void main(String[] args) throws Exception {
		if(args.length != 1) {
			System.err.println("Please specify the server's Bluetooth address");
			System.exit(1);
		}
		ContactId contactId = new ContactId(0);
		ClientCallback callback = new ClientCallback();
		// Store the server's Bluetooth address and UUID
		TransportProperties p = new TransportProperties();
		p.put("address", args[0]);
		p.put("uuid", BluetoothServerTest.UUID);
		callback.remote.put(contactId, p);
		// Create the plugin
		BluetoothPlugin plugin =
			new BluetoothPlugin(new ImmediateExecutor(), callback, 0L);
		// Start the plugin
		System.out.println("Starting plugin");
		plugin.start();
		// Try to connect to the server
		System.out.println("Creating connection");
		StreamTransportConnection conn = plugin.createConnection(contactId);
		if(conn == null) {
			System.out.println("Connection failed");
		} else {
			System.out.println("Connection created");
			Scanner in = new Scanner(conn.getInputStream());
			String challenge = in.nextLine();
			System.out.println("Received challenge: " + challenge);
			if(BluetoothServerTest.CHALLENGE.equals(challenge)) {
				PrintStream out = new PrintStream(conn.getOutputStream());
				out.println(RESPONSE);
				System.out.println("Sent response: " + RESPONSE);
			} else {
				System.out.println("Incorrect challenge");
			}
			conn.dispose(true);
		}
		// Stop the plugin
		System.out.println("Stopping plugin");
		plugin.stop();
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
