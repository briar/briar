package org.briarproject.plugins.tcp;

import org.briarproject.api.TransportConfig;
import org.briarproject.api.TransportProperties;
import org.briarproject.plugins.DuplexServerTest;

import java.util.Collections;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

// This is not a JUnit test - it has to be run manually while the client test
// is running on another machine
public class LanTcpServerTest extends DuplexServerTest {

	private static final int MAX_LATENCY = 60 * 1000;
	private static final int MAX_IDLE_TIME = 30 * 1000;
	private static final int POLLING_INTERVAL = 60 * 1000;

	private LanTcpServerTest(Executor executor) {
		callback = new ServerCallback(new TransportConfig(),
				new TransportProperties(),
				Collections.singletonMap(contactId, new TransportProperties()));
		plugin = new LanTcpPlugin(executor, callback, MAX_LATENCY,
				MAX_IDLE_TIME, POLLING_INTERVAL);
	}

	public static void main(String[] args) throws Exception {
		ExecutorService executor = Executors.newCachedThreadPool();
		try {
			new LanTcpServerTest(executor).run();
		} finally {
			executor.shutdown();
		}
	}
}
