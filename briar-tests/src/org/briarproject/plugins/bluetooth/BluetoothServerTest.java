package org.briarproject.plugins.bluetooth;

import org.briarproject.api.properties.TransportProperties;
import org.briarproject.api.settings.Settings;
import org.briarproject.plugins.DuplexServerTest;
import org.briarproject.system.SystemClock;

import java.security.SecureRandom;
import java.util.Collections;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

// This is not a JUnit test - it has to be run manually while the client test
// is running on another machine
public class BluetoothServerTest extends DuplexServerTest {

	private BluetoothServerTest(Executor executor) {
		// Store the UUID
		TransportProperties local = new TransportProperties();
		local.put("uuid", BluetoothTest.EMPTY_UUID);
		// Create the plugin
		callback = new ServerCallback(new Settings(), local,
				Collections.singletonMap(contactId, new TransportProperties()));
		plugin = new BluetoothPlugin(executor, new SystemClock(),
				new SecureRandom(), callback, 0, 0);
	}

	public static void main(String[] args) throws Exception {
		ExecutorService executor = Executors.newCachedThreadPool();
		try {
			new BluetoothServerTest(executor).run();
		} finally {
			executor.shutdown();
		}
	}
}
