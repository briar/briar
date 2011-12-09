package net.sf.briar.plugins.bluetooth;

import java.util.Collections;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

import net.sf.briar.api.TransportConfig;
import net.sf.briar.api.TransportProperties;
import net.sf.briar.plugins.StreamServerTest;

// This is not a JUnit test - it has to be run manually while the client test
// is running on another machine
public class BluetoothServerTest extends StreamServerTest {

	private BluetoothServerTest() {
		// Store the UUID
		TransportProperties local = new TransportProperties();
		local.put("uuid", BluetoothTest.UUID);
		// Create the plugin
		callback = new ServerCallback(new TransportConfig(), local,
				Collections.singletonMap(contactId, new TransportProperties()));
		ScheduledExecutorService executor = Executors.newScheduledThreadPool(1);
		plugin = new BluetoothPlugin(executor, callback, 0L);
	}

	public static void main(String[] args) throws Exception {
		new BluetoothServerTest().run();
	}
}
