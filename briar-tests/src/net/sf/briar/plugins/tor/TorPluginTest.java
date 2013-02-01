package net.sf.briar.plugins.tor;

import static java.util.concurrent.TimeUnit.SECONDS;

import java.io.PrintStream;
import java.util.Hashtable;
import java.util.Map;
import java.util.Scanner;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import net.sf.briar.BriarTestCase;
import net.sf.briar.api.ContactId;
import net.sf.briar.api.TransportConfig;
import net.sf.briar.api.TransportProperties;
import net.sf.briar.api.plugins.duplex.DuplexPluginCallback;
import net.sf.briar.api.plugins.duplex.DuplexTransportConnection;

import org.junit.Test;

public class TorPluginTest extends BriarTestCase {

	private final ContactId contactId = new ContactId(234);

	@Test
	public void testHiddenService() throws Exception {
		System.err.println("======== testHiddenService ========");
		Executor e = Executors.newCachedThreadPool();
		TorPlugin serverPlugin = null, clientPlugin = null;
		try {
			// Create a plugin instance for the server
			Callback serverCallback = new Callback();
			serverPlugin = new TorPlugin(e, serverCallback, 0, 0);
			System.out.println("Starting server plugin");
			serverPlugin.start();
			// The plugin should create a hidden service... eventually
			assertTrue(serverCallback.latch.await(600, SECONDS));
			System.out.println("Started server plugin");
			String onion = serverCallback.local.get("onion");
			assertNotNull(onion);
			assertTrue(onion.endsWith(".onion"));
			// Create another plugin instance for the client
			Callback clientCallback = new Callback();
			clientCallback.config.put("noHiddenService", "true");
			TransportProperties p = new TransportProperties();
			p.put("onion", onion);
			clientCallback.remote.put(contactId, p);
			clientPlugin = new TorPlugin(e, clientCallback, 0, 0);
			System.out.println("Starting client plugin");
			clientPlugin.start();
			// The plugin should start without creating a hidden service
			assertTrue(clientCallback.latch.await(600, SECONDS));
			System.out.println("Started client plugin");
			// Connect to the server's hidden service
			System.out.println("Connecting to hidden service");
			DuplexTransportConnection clientEnd =
					clientPlugin.createConnection(contactId);
			assertNotNull(clientEnd);
			DuplexTransportConnection serverEnd =
					serverCallback.incomingConnection;
			assertNotNull(serverEnd);
			System.out.println("Connected to hidden service");
			// Send some data through the Tor connection
			PrintStream out = new PrintStream(clientEnd.getOutputStream());
			out.println("Hello world");
			out.flush();
			Scanner in = new Scanner(serverEnd.getInputStream());
			assertTrue(in.hasNextLine());
			assertEquals("Hello world", in.nextLine());
			serverEnd.dispose(false, false);
			clientEnd.dispose(false, false);
		} finally {
			// Stop the plugins
			System.out.println("Stopping plugins");
			if(serverPlugin != null) serverPlugin.stop();
			if(clientPlugin != null) clientPlugin.stop();
			System.out.println("Stopped plugins");
		}
	}

	@Test
	public void testStoreAndRetrievePrivateKey() throws Exception {
		System.err.println("======== testStoreAndRetrievePrivateKey ========");
		Executor e = Executors.newCachedThreadPool();
		TorPlugin plugin = null;
		try {
			// Start a plugin instance with no private key
			Callback callback = new Callback();
			plugin = new TorPlugin(e, callback, 0, 0);
			System.out.println("Starting plugin without private key");
			plugin.start();
			// The plugin should create a hidden service... eventually
			assertTrue(callback.latch.await(600, SECONDS));
			System.out.println("Started plugin");
			String onion = callback.local.get("onion");
			assertNotNull(onion);
			assertTrue(onion.endsWith(".onion"));
			// Get the PEM-encoded private key
			String privateKey = callback.config.get("privateKey");
			assertNotNull(privateKey);
			// Stop the plugin
			System.out.println("Stopping plugin");
			plugin.stop();
			System.out.println("Stopped plugin");
			// Start another instance, reusing the private key
			callback = new Callback();
			callback.config.put("privateKey", privateKey);
			plugin = new TorPlugin(e, callback, 0, 0);
			System.out.println("Starting plugin with private key");
			plugin.start();
			// The plugin should create a hidden service... eventually
			assertTrue(callback.latch.await(600, SECONDS));
			System.out.println("Started plugin");
			// The onion URL should be the same
			assertEquals(onion, callback.local.get("onion"));
			// The private key should be the same
			assertEquals(privateKey, callback.config.get("privateKey"));
		} finally {
			// Stop the plugin
			System.out.println("Stopping plugin");
			if(plugin != null) plugin.stop();
			System.out.println("Stopped plugin");
		}
	}

	private static class Callback implements DuplexPluginCallback {

		private final Map<ContactId, TransportProperties> remote =
				new Hashtable<ContactId, TransportProperties>();
		private final CountDownLatch latch = new CountDownLatch(1);
		private final TransportConfig config = new TransportConfig();
		private final TransportProperties local = new TransportProperties();

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

		public void mergeConfig(TransportConfig c) {
			config.putAll(c);
		}

		public void mergeLocalProperties(TransportProperties p) {
			local.putAll(p);
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
