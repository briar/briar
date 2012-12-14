package net.sf.briar.plugins.modem;

import static java.util.logging.Level.INFO;

import java.util.Collections;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Logger;

import net.sf.briar.api.TransportConfig;
import net.sf.briar.api.TransportProperties;
import net.sf.briar.plugins.DuplexServerTest;

//This is not a JUnit test - it has to be run manually while the client test
//is running on another machine
public class ModemServerTest extends DuplexServerTest {

	private ModemServerTest(Executor executor) {
		// Create the plugin
		callback = new ServerCallback(new TransportConfig(),
				new TransportProperties(), Collections.singletonMap(contactId,
						new TransportProperties()));
		plugin = new ModemPlugin(executor, new ModemFactoryImpl(executor,
				new ReliabilityLayerFactoryImpl(executor)), callback, 0L);
	}

	public static void main(String[] args) throws Exception {
		Logger.getLogger("net.sf.briar").setLevel(INFO);
		ExecutorService executor = Executors.newCachedThreadPool();
		try {
			new ModemServerTest(executor).run();
		} finally {
			executor.shutdown();
		}
	}
}
