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
import net.sf.briar.api.plugins.StreamTransportCallback;
import net.sf.briar.api.transport.StreamTransportConnection;
import net.sf.briar.plugins.ImmediateExecutor;

import org.junit.Before;
import org.junit.Test;

public class SimpleSocketPluginTest extends TestCase {

	private final ContactId contactId = new ContactId(0);

	private TransportProperties localProperties = null;
	private Map<ContactId, TransportProperties> remoteProperties = null;
	private TransportConfig config = null;

	@Before
	public void setUp() {
		localProperties = new TransportProperties();
		remoteProperties = new HashMap<ContactId, TransportProperties>();
		remoteProperties.put(contactId, new TransportProperties());
		config = new TransportConfig();
	}

	@Test
	public void testIncomingConnection() throws Exception {
		StubCallback callback = new StubCallback();
		localProperties.put("host", "127.0.0.1");
		localProperties.put("port", "0");
		SimpleSocketPlugin plugin =
			new SimpleSocketPlugin(new ImmediateExecutor(), callback, 0L);
		plugin.start(localProperties, remoteProperties, config);
		// The plugin should have bound a socket and stored the port number
		assertNotNull(callback.localProperties);
		String host = callback.localProperties.get("host");
		assertNotNull(host);
		assertEquals("127.0.0.1", host);
		String portString = callback.localProperties.get("port");
		assertNotNull(portString);
		int port = Integer.valueOf(portString);
		assertTrue(port > 0 && port < 65536);
		// The plugin should be listening on the port
		InetSocketAddress addr = new InetSocketAddress(host, port);
		Socket s = new Socket();
		assertEquals(0, callback.incomingConnections);
		s.connect(addr, 100);
		Thread.sleep(10);
		assertEquals(1, callback.incomingConnections);
		s.close();
		// Stop the plugin
		plugin.stop();
		Thread.sleep(10);
		// The plugin should no longer be listening
		try {
			s = new Socket();
			s.connect(addr, 100);
			fail();
		} catch(IOException expected) {}
	}

	@Test
	public void testOutgoingConnection() throws Exception {
		StubCallback callback = new StubCallback();
		SimpleSocketPlugin plugin =
			new SimpleSocketPlugin(new ImmediateExecutor(), callback, 0L);
		plugin.start(localProperties, remoteProperties, config);
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
		TransportProperties properties = new TransportProperties();
		properties.put("host", "127.0.0.1");
		properties.put("port", String.valueOf(port));
		plugin.setRemoteProperties(contactId, properties);
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

	@Test
	public void testUpdatingPropertiesReopensSocket() throws Exception {
		StubCallback callback = new StubCallback();
		localProperties.put("host", "127.0.0.1");
		localProperties.put("port", "0");
		SimpleSocketPlugin plugin =
			new SimpleSocketPlugin(new ImmediateExecutor(), callback, 0L);
		plugin.start(localProperties, remoteProperties, config);
		// The plugin should have bound a socket and stored the port number
		assertNotNull(callback.localProperties);
		String host = callback.localProperties.get("host");
		assertNotNull(host);
		assertEquals("127.0.0.1", host);
		String portString = callback.localProperties.get("port");
		assertNotNull(portString);
		int port = Integer.valueOf(portString);
		assertTrue(port > 0 && port < 65536);
		// The plugin should be listening on the port
		InetSocketAddress addr = new InetSocketAddress(host, port);
		Socket s = new Socket();
		assertEquals(0, callback.incomingConnections);
		s.connect(addr, 100);
		Thread.sleep(10);
		assertEquals(1, callback.incomingConnections);
		s.close();
		// Update the local properties with a new port number
		localProperties.put("port", "0");
		plugin.setLocalProperties(localProperties);
		// The plugin should no longer be listening on the old port
		try {
			s = new Socket();
			s.connect(addr, 100);
			fail();
		} catch(IOException expected) {}
		// Find out what the new port is
		portString = callback.localProperties.get("port");
		assertNotNull(portString);
		int newPort = Integer.valueOf(portString);
		assertFalse(newPort == port);
		// The plugin should be listening on the new port
		addr = new InetSocketAddress(host, newPort);
		assertEquals(1, callback.incomingConnections);
		s = new Socket();
		s.connect(addr, 100);
		Thread.sleep(10);
		assertEquals(2, callback.incomingConnections);
		s.close();
		// Stop the plugin
		plugin.stop();
		Thread.sleep(10);
		// The plugin should no longer be listening
		try {
			s = new Socket();
			s.connect(addr, 100);
			fail();
		} catch(IOException expected) {}
	}

	private static class StubCallback implements StreamTransportCallback {

		public TransportProperties localProperties = null;
		public volatile int incomingConnections = 0;

		public void setLocalProperties(TransportProperties properties) {
			localProperties = properties;
		}

		public void setConfig(TransportConfig config) {
		}

		public void showMessage(String... message) {
		}

		public boolean showConfirmationMessage(String... message) {
			return false;
		}

		public int showChoice(String[] choices, String... message) {
			return -1;
		}

		public void incomingConnectionCreated(StreamTransportConnection c) {
			incomingConnections++;
		}

		public void outgoingConnectionCreated(ContactId contactId,
				StreamTransportConnection c) {
		}
	}
}
