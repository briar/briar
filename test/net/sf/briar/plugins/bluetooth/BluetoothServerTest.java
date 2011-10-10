package net.sf.briar.plugins.bluetooth;

import java.io.IOException;
import java.io.PrintStream;
import java.util.Collections;
import java.util.Map;
import java.util.Scanner;
import java.util.TreeMap;

import net.sf.briar.api.ContactId;
import net.sf.briar.api.plugins.StreamTransportCallback;
import net.sf.briar.api.transport.StreamTransportConnection;
import net.sf.briar.plugins.ImmediateExecutor;

//This is not a JUnit test - it has to be run manually while the server test
//is running on another machine
public class BluetoothServerTest {

	public static final String UUID = "CABBA6E5CABBA6E5CABBA6E5CABBA6E5";
	public static final String CHALLENGE = "Potatoes!";

	public static void main(String[] args) throws Exception {
		Map<String, String> localProperties = Collections.emptyMap();
		Map<ContactId, Map<String, String>> remoteProperties =
			Collections.emptyMap();
		Map<String, String> config = new TreeMap<String, String>();
		StreamTransportCallback callback = new ServerCallback();
		// Store the UUID
		config.put("uuid", UUID);
		// Create the plugin
		BluetoothPlugin plugin =
			new BluetoothPlugin(new ImmediateExecutor(), callback, 0L);
		// Start the plugin
		System.out.println("Starting plugin");
		plugin.start(localProperties, remoteProperties, config);
		// Wait for a connection
		System.out.println("Waiting for connection");
		synchronized(callback) {
			callback.wait();
		}
		// Stop the plugin
		System.out.println("Stopping plugin");
		plugin.stop();
	}

	private static class ServerCallback implements StreamTransportCallback {

		public void setLocalProperties(Map<String, String> properties) {}

		public void setConfig(Map<String, String> config) {}

		public void showMessage(String... message) {}

		public boolean showConfirmationMessage(String... message) {
			return false;
		}

		public int showChoice(String[] choices, String... message) {
			return -1;
		}

		public void incomingConnectionCreated(StreamTransportConnection conn) {
			System.out.println("Connection received");
			try {
				PrintStream out = new PrintStream(conn.getOutputStream());
				out.println(CHALLENGE);
				System.out.println("Sent challenge: " + CHALLENGE);
				Scanner in = new Scanner(conn.getInputStream());
				String response = in.nextLine();
				System.out.println("Received response: " + response);
				if(BluetoothClientTest.RESPONSE.equals(response)) {
					System.out.println("Correct response");
				} else {
					System.out.println("Incorrect response");
				}
				conn.dispose(true);
			} catch(IOException e) {
				e.printStackTrace();
			}
			synchronized(this) {
				notifyAll();
			}
		}

		public void outgoingConnectionCreated(ContactId contactId,
				StreamTransportConnection c) {}
	}
}
