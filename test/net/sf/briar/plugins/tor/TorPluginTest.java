package net.sf.briar.plugins.tor;

import java.util.Hashtable;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import net.sf.briar.BriarTestCase;
import net.sf.briar.api.ContactId;
import net.sf.briar.api.TransportConfig;
import net.sf.briar.api.TransportProperties;
import net.sf.briar.api.plugins.duplex.DuplexPluginCallback;
import net.sf.briar.api.plugins.duplex.DuplexTransportConnection;

import org.junit.Test;

public class TorPluginTest extends BriarTestCase {

	private final ContactId contactId = new ContactId(1);

	@Test
	public void testHiddenService() throws Exception {
		Executor e = Executors.newCachedThreadPool();
		// Create a plugin instance for the server
		Callback serverCallback = new Callback();
		TorPlugin serverPlugin = new TorPlugin(e, serverCallback, 0L);
		serverPlugin.start();
		// The plugin should create a hidden service... eventually
		serverCallback.latch.await(5, TimeUnit.MINUTES);
		String onion = serverCallback.local.get("onion");
		assertNotNull(onion);
		assertTrue(onion.endsWith(".onion"));
		// Create another plugin instance for the client
		Callback clientCallback = new Callback();
		TransportProperties p = new TransportProperties();
		p.put("onion", onion);
		clientCallback.remote.put(contactId, p);
		TorPlugin clientPlugin = new TorPlugin(e, clientCallback, 0L);
		clientPlugin.start();
		// Connect to the server's hidden service
		DuplexTransportConnection c = clientPlugin.createConnection(contactId);
		assertNotNull(c);
		c.dispose(false, false);
		assertEquals(1, serverCallback.incomingConnections);
		// Stop the plugins
		serverPlugin.stop();
		clientPlugin.stop();
	}

	private static class Callback implements DuplexPluginCallback {

		private final Map<ContactId, TransportProperties> remote =
			new Hashtable<ContactId, TransportProperties>();
		private final CountDownLatch latch = new CountDownLatch(1);

		private TransportConfig config = new TransportConfig();
		private TransportProperties local = new TransportProperties();

		private volatile int incomingConnections = 0;

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
			latch.countDown();
			local = p;
		}

		public int showChoice(String[] options, String... message) {
			return -1;
		}

		public boolean showConfirmationMessage(String... message) {
			return false;
		}

		public void showMessage(String... message) {}

		public void incomingConnectionCreated(DuplexTransportConnection d) {
			incomingConnections++;
		}

		public void outgoingConnectionCreated(ContactId c,
				DuplexTransportConnection d) {}
	}
}
