package net.sf.briar.plugins.socket;

import java.util.Collections;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import net.sf.briar.api.ContactId;
import net.sf.briar.api.TransportConfig;
import net.sf.briar.api.TransportProperties;
import net.sf.briar.plugins.DuplexClientTest;

// This is not a JUnit test - it has to be run manually while the server test
// is running on another machine
public class LanSocketClientTest extends DuplexClientTest {

	private LanSocketClientTest(Executor executor, String serverAddress,
			String serverPort) {
		// Store the server's internal address and port
		TransportProperties p = new TransportProperties();
		p.put("internal", serverAddress);
		p.put("port", serverPort);
		Map<ContactId, TransportProperties> remote =
			Collections.singletonMap(contactId, p);
		// Create the plugin
		callback = new ClientCallback(new TransportConfig(),
				new TransportProperties(), remote);
		plugin = new LanSocketPlugin(executor, callback, 0L);
	}

	public static void main(String[] args) throws Exception {
		if(args.length != 2) {
			System.err.println("Please specify the server's address and port");
			System.exit(1);
		}
		ExecutorService executor = Executors.newCachedThreadPool();
		try {
			new LanSocketClientTest(executor, args[0], args[1]).run();
		} finally {
			executor.shutdown();
		}
	}
}
