package net.sf.briar.plugins.modem;

import java.util.Collections;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import net.sf.briar.api.ContactId;
import net.sf.briar.api.TransportConfig;
import net.sf.briar.api.TransportProperties;
import net.sf.briar.plugins.DuplexClientTest;

//This is not a JUnit test - it has to be run manually while the server test
//is running on another machine
public class ModemClientTest extends DuplexClientTest {

	private ModemClientTest(Executor executor, String number) {
		// Store the server's phone number
		TransportProperties p = new TransportProperties();
		p.put("number", number);
		Map<ContactId, TransportProperties> remote =
				Collections.singletonMap(contactId, p);
		// Create the plugin
		callback = new ClientCallback(new TransportConfig(),
				new TransportProperties(), remote);
		plugin = new ModemPlugin(executor, new ModemFactoryImpl(executor),
				callback, 0L);
	}

	public static void main(String[] args) throws Exception {
		if(args.length != 1) {
			System.err.println("Please specify the server's phone number");
			System.exit(1);
		}
		ExecutorService executor = Executors.newCachedThreadPool();
		try {
			new ModemClientTest(executor, args[0]).run();
		} finally {
			executor.shutdown();
		}
	}

}
