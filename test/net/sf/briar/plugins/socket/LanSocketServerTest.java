package net.sf.briar.plugins.socket;

import java.util.Collections;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import net.sf.briar.api.TransportConfig;
import net.sf.briar.api.TransportProperties;
import net.sf.briar.plugins.StreamServerTest;

//This is not a JUnit test - it has to be run manually while the server test
//is running on another machine
public class LanSocketServerTest extends StreamServerTest {

	private LanSocketServerTest() {
		callback = new ServerCallback(new TransportConfig(),
				new TransportProperties(),
				Collections.singletonMap(contactId, new TransportProperties()));
		Executor e = Executors.newCachedThreadPool();
		plugin = new LanSocketPlugin(e, callback, 0L);
	}

	public static void main(String[] args) throws Exception {
		new LanSocketServerTest().run();
	}
}
