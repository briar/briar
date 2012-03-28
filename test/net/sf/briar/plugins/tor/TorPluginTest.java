package net.sf.briar.plugins.tor;

import java.io.PrintStream;
import java.util.Hashtable;
import java.util.Map;
import java.util.Scanner;
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
		serverCallback.latch.await(10, TimeUnit.MINUTES);
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
		DuplexTransportConnection clientEnd =
			clientPlugin.createConnection(contactId);
		assertNotNull(clientEnd);
		DuplexTransportConnection serverEnd = serverCallback.incomingConnection;
		assertNotNull(serverEnd);
		// Send some data through the Tor connection
		PrintStream out = new PrintStream(clientEnd.getOutputStream());
		out.println("Hello world");
		out.flush();
		Scanner in = new Scanner(serverEnd.getInputStream());
		assertTrue(in.hasNextLine());
		assertEquals("Hello world", in.nextLine());
		serverEnd.dispose(false, false);
		clientEnd.dispose(false, false);
		// Stop the plugins
		serverPlugin.stop();
		clientPlugin.stop();
	}

	@Test
	public void testStoreAndRetrievePrivateKey() throws Exception {
		Executor e = Executors.newCachedThreadPool();
		// Start a plugin instance with no private key
		Callback callback = new Callback();
		TorPlugin plugin = new TorPlugin(e, callback, 0L);
		plugin.start();
		// The plugin should create a hidden service... eventually
		callback.latch.await(10, TimeUnit.MINUTES);
		String onion = callback.local.get("onion");
		assertNotNull(onion);
		assertTrue(onion.endsWith(".onion"));
		// Get the PEM-encoded private key and stop the plugin
		String privateKey = callback.config.get("privateKey");
		assertNotNull(privateKey);
		plugin.stop();
		// Start another instance, reusing the private key
		callback = new Callback();
		callback.config.put("privateKey", privateKey);
		plugin = new TorPlugin(e, callback, 0L);
		plugin.start();
		// The plugin should create a hidden service... eventually
		callback.latch.await(10, TimeUnit.MINUTES);
		// The onion URL should be the same
		assertEquals(onion, callback.local.get("onion"));
		// The private key should be the same
		assertEquals(privateKey, callback.config.get("privateKey"));
		// Stop the plugin
		plugin.stop();
	}

	private static class Callback implements DuplexPluginCallback {

		private final Map<ContactId, TransportProperties> remote =
			new Hashtable<ContactId, TransportProperties>();
		private final CountDownLatch latch = new CountDownLatch(1);

		private TransportConfig config = new TransportConfig();
		private TransportProperties local = new TransportProperties();

		private volatile DuplexTransportConnection incomingConnection = null;

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
			local = p;
			latch.countDown();
		}

		public int showChoice(String[] options, String... message) {
			return -1;
		}

		public boolean showConfirmationMessage(String... message) {
			return false;
		}

		public void showMessage(String... message) {}

		public void incomingConnectionCreated(DuplexTransportConnection d) {
			incomingConnection = d;
		}

		public void outgoingConnectionCreated(ContactId c,
				DuplexTransportConnection d) {}
	}
}
