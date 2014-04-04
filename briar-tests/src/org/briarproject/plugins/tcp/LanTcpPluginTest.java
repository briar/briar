package org.briarproject.plugins.tcp;

import static java.util.concurrent.TimeUnit.SECONDS;

import java.io.IOException;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Hashtable;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

import org.briarproject.BriarTestCase;
import org.briarproject.api.ContactId;
import org.briarproject.api.TransportConfig;
import org.briarproject.api.TransportProperties;
import org.briarproject.api.plugins.duplex.DuplexPlugin;
import org.briarproject.api.plugins.duplex.DuplexPluginCallback;
import org.briarproject.api.plugins.duplex.DuplexTransportConnection;
import org.junit.Test;

public class LanTcpPluginTest extends BriarTestCase {

	private final ContactId contactId = new ContactId(234);

	@Test
	public void testIncomingConnection() throws Exception {
		Callback callback = new Callback();
		Executor executor = Executors.newCachedThreadPool();
		DuplexPlugin plugin = new LanTcpPlugin(executor, callback, 0, 0, 0);
		plugin.start();
		// The plugin should have bound a socket and stored the port number
		assertTrue(callback.propertiesLatch.await(5, SECONDS));
		String addrString = callback.local.get("address");
		assertNotNull(addrString);
		InetAddress addr = InetAddress.getByName(addrString);
		assertTrue(addr instanceof Inet4Address);
		assertFalse(addr.isLoopbackAddress());
		assertTrue(addr.isLinkLocalAddress() || addr.isSiteLocalAddress());
		String portString = callback.local.get("port");
		assertNotNull(portString);
		int port = Integer.parseInt(portString);
		assertTrue(port > 0 && port < 65536);
		// The plugin should be listening on the port
		InetSocketAddress socketAddr = new InetSocketAddress(addr, port);
		Socket s = new Socket();
		s.connect(socketAddr, 100);
		assertTrue(callback.connectionsLatch.await(5, SECONDS));
		s.close();
		// Stop the plugin
		plugin.stop();
	}

	@Test
	public void testOutgoingConnection() throws Exception {
		Callback callback = new Callback();
		Executor executor = Executors.newCachedThreadPool();
		DuplexPlugin plugin = new LanTcpPlugin(executor, callback, 0, 0, 0);
		plugin.start();
		// Listen on a local port
		final ServerSocket ss = new ServerSocket();
		ss.bind(new InetSocketAddress("127.0.0.1", 0), 10);
		int port = ss.getLocalPort();
		final CountDownLatch latch = new CountDownLatch(1);
		final AtomicBoolean error = new AtomicBoolean(false);
		new Thread() {
			@Override
			public void run() {
				try {
					ss.accept();
					latch.countDown();
				} catch(IOException e) {
					error.set(true);
				}
			}
		}.start();
		// Tell the plugin about the port
		TransportProperties p = new TransportProperties();
		p.put("address", "127.0.0.1");
		p.put("port", String.valueOf(port));
		callback.remote.put(contactId, p);
		// Connect to the port
		DuplexTransportConnection d = plugin.createConnection(contactId);
		assertNotNull(d);
		// Check that the connection was accepted
		assertTrue(latch.await(5, SECONDS));
		assertFalse(error.get());
		// Clean up
		d.dispose(false, true);
		ss.close();
		plugin.stop();
	}

	private static class Callback implements DuplexPluginCallback {

		private final Map<ContactId, TransportProperties> remote =
				new Hashtable<ContactId, TransportProperties>();
		private final CountDownLatch propertiesLatch = new CountDownLatch(1);
		private final CountDownLatch connectionsLatch = new CountDownLatch(1);
		private final TransportProperties local = new TransportProperties();

		public TransportConfig getConfig() {
			return new TransportConfig();
		}

		public TransportProperties getLocalProperties() {
			return local;
		}

		public Map<ContactId, TransportProperties> getRemoteProperties() {
			return remote;
		}

		public void mergeConfig(TransportConfig c) {}

		public void mergeLocalProperties(TransportProperties p) {
			local.putAll(p);
			propertiesLatch.countDown();
		}

		public int showChoice(String[] options, String... message) {
			return -1;
		}

		public boolean showConfirmationMessage(String... message) {
			return false;
		}

		public void showMessage(String... message) {}

		public void pollNow() {}

		public void incomingConnectionCreated(DuplexTransportConnection d) {
			connectionsLatch.countDown();
		}

		public void outgoingConnectionCreated(ContactId c,
				DuplexTransportConnection d) {}
	}
}
