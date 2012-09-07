package net.sf.briar.plugins.bluetooth;

import java.util.Collections;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import net.sf.briar.api.TransportConfig;
import net.sf.briar.api.TransportProperties;
import net.sf.briar.api.clock.SystemClock;
import net.sf.briar.plugins.DuplexServerTest;

// This is not a JUnit test - it has to be run manually while the client test
// is running on another machine
public class BluetoothServerTest extends DuplexServerTest {

	private BluetoothServerTest(Executor executor) {
		// Store the UUID
		TransportProperties local = new TransportProperties();
		local.put("uuid", BluetoothTest.getUuid());
		// Create the plugin
		callback = new ServerCallback(new TransportConfig(), local,
				Collections.singletonMap(contactId, new TransportProperties()));
		plugin = new BluetoothPlugin(executor, new SystemClock(), callback, 0L);
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
