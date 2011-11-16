package net.sf.briar.plugins.socket;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import junit.framework.TestCase;
import net.sf.briar.api.ContactId;
import net.sf.briar.api.TransportConfig;
import net.sf.briar.api.TransportProperties;
import net.sf.briar.api.plugins.StreamPluginCallback;
import net.sf.briar.api.transport.StreamTransportConnection;
import net.sf.briar.plugins.ImmediateExecutor;

import org.junit.Test;

public class SimpleSocketPluginTest extends TestCase {

	private final ContactId contactId = new ContactId(0);

	@Test
	public void testIncomingConnection() throws Exception {
		StreamCallback callback = new StreamCallback();
		callback.local.put("internal", "127.0.0.1");
		callback.local.put("port", "0");
		SimpleSocketPlugin plugin =
			new SimpleSocketPlugin(new ImmediateExecutor(), callback, 0L);
		plugin.start();
		// The plugin should have bound a socket and stored the port number
		String host = callback.local.get("internal");
		assertNotNull(host);
		assertEquals("127.0.0.1", host);
		String portString = callback.local.get("port");
		assertNotNull(portString);
		int port = Integer.valueOf(portString);
		assertTrue(port > 0 && port < 65536);
		// The plugin should be listening on the port
		InetSocketAddress addr = new InetSocketAddress(host, port);
		Socket s = new Socket();
		assertEquals(0, callback.incomingConnections);
		s.connect(addr, 100);
		Thread.sleep(100);
		assertEquals(1, callback.incomingConnections);
		s.close();
		// Stop the plugin
		plugin.stop();
		Thread.sleep(100);
		// The plugin should no longer be listening
		try {
			s = new Socket();
			s.connect(addr, 100);
			fail();
		} catch(IOException expected) {}
	}

	@Test
	public void testOutgoingConnection() throws Exception {
		StreamCallback callback = new StreamCallback();
		SimpleSocketPlugin plugin =
			new SimpleSocketPlugin(new ImmediateExecutor(), callback, 0L);
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
		p.put("internal", "127.0.0.1");
		p.put("port", String.valueOf(port));
		callback.remote.put(contactId, p);
		// Connect to the port
		StreamTransportConnection conn = plugin.createConnection(contactId);
		assertNotNull(conn);
		// Check that the connection was accepted
		assertTrue(latch.await(1, TimeUnit.SECONDS));
		assertFalse(error.get());
		// Clean up
		conn.dispose(true);
		ss.close();
		plugin.stop();
	}

	private static class StreamCallback implements StreamPluginCallback {

		private TransportConfig config = new TransportConfig();
		private TransportProperties local = new TransportProperties();
		private final Map<ContactId, TransportProperties> remote =
			new HashMap<ContactId, TransportProperties>();

		private int incomingConnections = 0;

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
		}

		public int showChoice(String[] options, String... message) {
			return -1;
		}

		public boolean showConfirmationMessage(String... message) {
			return false;
		}

		public void showMessage(String... message) {}

		public void incomingConnectionCreated(StreamTransportConnection c) {
			incomingConnections++;
		}

		public void outgoingConnectionCreated(ContactId contactId,
				StreamTransportConnection c) {
		}
	}
}
