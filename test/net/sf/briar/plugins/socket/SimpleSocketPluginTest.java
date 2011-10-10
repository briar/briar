package net.sf.briar.plugins.socket;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import junit.framework.TestCase;
import net.sf.briar.api.ContactId;
import net.sf.briar.api.transport.StreamTransportConnection;
import net.sf.briar.plugins.ImmediateExecutor;
import net.sf.briar.plugins.StubStreamCallback;

import org.junit.Before;
import org.junit.Test;

public class SimpleSocketPluginTest extends TestCase {

	private final ContactId contactId = new ContactId(0);

	private Map<String, String> localProperties = null;
	private Map<ContactId, Map<String, String>> remoteProperties = null;
	private Map<String, String> config = null;

	@Before
	public void setUp() {
		localProperties = new TreeMap<String, String>();
		remoteProperties = new HashMap<ContactId, Map<String, String>>();
		remoteProperties.put(contactId, new TreeMap<String, String>());
		config = new TreeMap<String, String>();
	}

	@Test
	public void testIncomingConnection() throws Exception {
		StubStreamCallback callback = new StubStreamCallback();
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
		StubStreamCallback callback = new StubStreamCallback();
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
		Map<String, String> properties = new TreeMap<String, String>();
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
		StubStreamCallback callback = new StubStreamCallback();
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
}
