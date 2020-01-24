package org.briarproject.bramble.plugin.tcp;

import org.briarproject.bramble.api.data.BdfList;
import org.briarproject.bramble.api.keyagreement.KeyAgreementListener;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.plugin.Backoff;
import org.briarproject.bramble.api.plugin.Plugin.State;
import org.briarproject.bramble.api.plugin.PluginCallback;
import org.briarproject.bramble.api.plugin.TransportConnectionReader;
import org.briarproject.bramble.api.plugin.TransportConnectionWriter;
import org.briarproject.bramble.api.plugin.duplex.DuplexTransportConnection;
import org.briarproject.bramble.api.properties.TransportProperties;
import org.briarproject.bramble.api.settings.Settings;
import org.briarproject.bramble.test.BrambleTestCase;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;

import static java.net.NetworkInterface.getNetworkInterfaces;
import static java.util.Collections.list;
import static java.util.concurrent.Executors.newCachedThreadPool;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.briarproject.bramble.api.keyagreement.KeyAgreementConstants.COMMIT_LENGTH;
import static org.briarproject.bramble.api.keyagreement.KeyAgreementConstants.TRANSPORT_ID_LAN;
import static org.briarproject.bramble.api.plugin.Plugin.PREF_PLUGIN_ENABLE;
import static org.briarproject.bramble.plugin.tcp.LanTcpPlugin.areAddressesInSameNetwork;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

public class LanTcpPluginTest extends BrambleTestCase {

	private final Backoff backoff = new TestBackoff();
	private final ExecutorService ioExecutor = newCachedThreadPool();

	private Callback callback = null;
	private LanTcpPlugin plugin = null;

	@Before
	public void setUp() {
		callback = new Callback();
		plugin = new LanTcpPlugin(ioExecutor, backoff, callback, 0, 0, 1000) {
			@Override
			protected boolean canConnectToOwnAddress() {
				return true;
			}
		};
	}

	@Test
	public void testAreAddressesInSameNetwork() {
		// Local and remote in 10.0.0.0/8
		assertTrue(areAddressesInSameNetwork(makeAddress(10, 0, 0, 0),
				makeAddress(10, 255, 255, 255), 8));
		assertFalse(areAddressesInSameNetwork(makeAddress(10, 0, 0, 0),
				makeAddress(10, 255, 255, 255), 9));

		// Local and remote in 172.16.0.0/12
		assertTrue(areAddressesInSameNetwork(makeAddress(172, 16, 0, 0),
				makeAddress(172, 31, 255, 255), 12));
		assertFalse(areAddressesInSameNetwork(makeAddress(172, 16, 0, 0),
				makeAddress(172, 31, 255, 255), 13));

		// Local and remote in 192.168.0.0/16
		assertTrue(areAddressesInSameNetwork(makeAddress(192, 168, 0, 0),
				makeAddress(192, 168, 255, 255), 16));
		assertFalse(areAddressesInSameNetwork(makeAddress(192, 168, 0, 0),
				makeAddress(192, 168, 255, 255), 17));

		// Local and remote in 169.254.0.0/16
		assertTrue(areAddressesInSameNetwork(makeAddress(169, 254, 0, 0),
				makeAddress(169, 254, 255, 255), 16));
		assertFalse(areAddressesInSameNetwork(makeAddress(169, 254, 0, 0),
				makeAddress(169, 254, 255, 255), 17));

		// Local in 10.0.0.0/8, remote in a different network
		assertFalse(areAddressesInSameNetwork(makeAddress(10, 0, 0, 0),
				makeAddress(172, 31, 255, 255), 8));
		assertFalse(areAddressesInSameNetwork(makeAddress(10, 0, 0, 0),
				makeAddress(192, 168, 255, 255), 8));
		assertFalse(areAddressesInSameNetwork(makeAddress(10, 0, 0, 0),
				makeAddress(169, 254, 255, 255), 8));

		// Local in 172.16.0.0/12, remote in a different network
		assertFalse(areAddressesInSameNetwork(makeAddress(172, 16, 0, 0),
				makeAddress(10, 255, 255, 255), 12));
		assertFalse(areAddressesInSameNetwork(makeAddress(172, 16, 0, 0),
				makeAddress(192, 168, 255, 255), 12));
		assertFalse(areAddressesInSameNetwork(makeAddress(172, 16, 0, 0),
				makeAddress(169, 254, 255, 255), 12));

		// Local in 192.168.0.0/16, remote in a different network
		assertFalse(areAddressesInSameNetwork(makeAddress(192, 168, 0, 0),
				makeAddress(10, 255, 255, 255), 16));
		assertFalse(areAddressesInSameNetwork(makeAddress(192, 168, 0, 0),
				makeAddress(172, 31, 255, 255), 16));
		assertFalse(areAddressesInSameNetwork(makeAddress(192, 168, 0, 0),
				makeAddress(169, 254, 255, 255), 16));

		// Local in 169.254.0.0/16, remote in a different network
		assertFalse(areAddressesInSameNetwork(makeAddress(169, 254, 0, 0),
				makeAddress(10, 255, 255, 255), 16));
		assertFalse(areAddressesInSameNetwork(makeAddress(169, 254, 0, 0),
				makeAddress(172, 31, 255, 255), 16));
		assertFalse(areAddressesInSameNetwork(makeAddress(169, 254, 0, 0),
				makeAddress(192, 168, 255, 255), 16));
	}

	private byte[] makeAddress(int... parts) {
		byte[] b = new byte[parts.length];
		for (int i = 0; i < parts.length; i++) b[i] = (byte) parts[i];
		return b;
	}

	@Test
	public void testIncomingConnection() throws Exception {
		assumeTrue(systemHasLocalIpv4Address());
		plugin.start();
		// The plugin should have bound a socket and stored the port number
		assertTrue(callback.propertiesLatch.await(5, SECONDS));
		String ipPorts = callback.local.get("ipPorts");
		assertNotNull(ipPorts);
		String[] split = ipPorts.split(",");
		assertEquals(1, split.length);
		split = split[0].split(":");
		assertEquals(2, split.length);
		String addrString = split[0], portString = split[1];
		InetAddress addr = InetAddress.getByName(addrString);
		assertTrue(addr instanceof Inet4Address);
		assertFalse(addr.isLoopbackAddress());
		assertTrue(addr.isLinkLocalAddress() || addr.isSiteLocalAddress());
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
		assumeTrue(systemHasLocalIpv4Address());
		plugin.start();
		// The plugin should have bound a socket and stored the port number
		assertTrue(callback.propertiesLatch.await(5, SECONDS));
		assertTrue(callback.propertiesLatch.await(5, SECONDS));
		String ipPorts = callback.local.get("ipPorts");
		assertNotNull(ipPorts);
		String[] split = ipPorts.split(",");
		assertEquals(1, split.length);
		split = split[0].split(":");
		assertEquals(2, split.length);
		String addrString = split[0];
		// Listen on the same interface as the plugin
		ServerSocket ss = new ServerSocket();
		ss.bind(new InetSocketAddress(addrString, 0), 10);
		int port = ss.getLocalPort();
		CountDownLatch latch = new CountDownLatch(1);
		AtomicBoolean error = new AtomicBoolean(false);
		new Thread(() -> {
			try {
				ss.accept();
				latch.countDown();
			} catch (IOException e) {
				error.set(true);
			}
		}).start();
		// Connect to the port
		TransportProperties p = new TransportProperties();
		p.put("ipPorts", addrString + ":" + port);
		DuplexTransportConnection d = plugin.createConnection(p);
		assertNotNull(d);
		// Check that the connection was accepted
		assertTrue(latch.await(5, SECONDS));
		assertFalse(error.get());
		// Clean up
		d.getReader().dispose(false, true);
		d.getWriter().dispose(false);
		ss.close();
		plugin.stop();
	}

	@Test
	public void testIncomingKeyAgreementConnection() throws Exception {
		assumeTrue(systemHasLocalIpv4Address());
		plugin.start();
		assertTrue(callback.propertiesLatch.await(5, SECONDS));
		KeyAgreementListener kal =
				plugin.createKeyAgreementListener(new byte[COMMIT_LENGTH]);
		assertNotNull(kal);
		CountDownLatch latch = new CountDownLatch(1);
		AtomicBoolean error = new AtomicBoolean(false);
		new Thread(() -> {
			try {
				kal.accept();
				latch.countDown();
			} catch (IOException e) {
				error.set(true);
			}
		}).start();
		// The plugin should have bound a socket and stored the port number
		BdfList descriptor = kal.getDescriptor();
		assertEquals(3, descriptor.size());
		assertEquals(TRANSPORT_ID_LAN, descriptor.getLong(0).longValue());
		byte[] address = descriptor.getRaw(1);
		InetAddress addr = InetAddress.getByAddress(address);
		assertTrue(addr instanceof Inet4Address);
		assertFalse(addr.isLoopbackAddress());
		assertTrue(addr.isLinkLocalAddress() || addr.isSiteLocalAddress());
		int port = descriptor.getLong(2).intValue();
		assertTrue(port > 0 && port < 65536);
		// The plugin should be listening on the port
		InetSocketAddress socketAddr = new InetSocketAddress(addr, port);
		Socket s = new Socket();
		s.connect(socketAddr, 100);
		// Check that the connection was accepted
		assertTrue(latch.await(5, SECONDS));
		assertFalse(error.get());
		// Clean up
		s.close();
		kal.close();
		plugin.stop();
	}

	@Test
	public void testOutgoingKeyAgreementConnection() throws Exception {
		assumeTrue(systemHasLocalIpv4Address());
		plugin.start();
		// The plugin should have bound a socket and stored the port number
		assertTrue(callback.propertiesLatch.await(5, SECONDS));
		String ipPorts = callback.local.get("ipPorts");
		assertNotNull(ipPorts);
		String[] split = ipPorts.split(",");
		assertEquals(1, split.length);
		split = split[0].split(":");
		assertEquals(2, split.length);
		String addrString = split[0];
		// Listen on the same interface as the plugin
		ServerSocket ss = new ServerSocket();
		ss.bind(new InetSocketAddress(addrString, 0), 10);
		CountDownLatch latch = new CountDownLatch(1);
		AtomicBoolean error = new AtomicBoolean(false);
		new Thread(() -> {
			try {
				ss.accept();
				latch.countDown();
			} catch (IOException e) {
				error.set(true);
			}
		}).start();
		// Tell the plugin about the port
		BdfList descriptor = new BdfList();
		descriptor.add(TRANSPORT_ID_LAN);
		InetSocketAddress local =
				(InetSocketAddress) ss.getLocalSocketAddress();
		descriptor.add(local.getAddress().getAddress());
		descriptor.add(local.getPort());
		// Connect to the port
		DuplexTransportConnection d = plugin.createKeyAgreementConnection(
				new byte[COMMIT_LENGTH], descriptor);
		assertNotNull(d);
		// Check that the connection was accepted
		assertTrue(latch.await(5, SECONDS));
		assertFalse(error.get());
		// Clean up
		d.getReader().dispose(false, true);
		d.getWriter().dispose(false);
		ss.close();
		plugin.stop();
	}

	private boolean systemHasLocalIpv4Address() throws Exception {
		for (NetworkInterface i : list(getNetworkInterfaces())) {
			for (InetAddress a : list(i.getInetAddresses())) {
				if (a instanceof Inet4Address) {
					return a.isLinkLocalAddress() || a.isSiteLocalAddress();
				}
			}
		}
		return false;
	}

	@NotNullByDefault
	private static class Callback implements PluginCallback {

		// Properties will be stored twice: the preferred port at startup,
		// and the IP:port when the server socket is bound
		private final CountDownLatch propertiesLatch = new CountDownLatch(2);
		private final CountDownLatch connectionsLatch = new CountDownLatch(1);
		private final TransportProperties local = new TransportProperties();
		private final Settings settings = new Settings();

		private Callback() {
			settings.putBoolean(PREF_PLUGIN_ENABLE, true);
		}

		@Override
		public Settings getSettings() {
			return settings;
		}

		@Override
		public TransportProperties getLocalProperties() {
			return local;
		}

		@Override
		public void mergeSettings(Settings s) {
		}

		@Override
		public void mergeLocalProperties(TransportProperties p) {
			local.putAll(p);
			propertiesLatch.countDown();
		}

		@Override
		public void pluginStateChanged(State newState) {
		}

		@Override
		public void handleConnection(DuplexTransportConnection d) {
			connectionsLatch.countDown();
		}

		@Override
		public void handleReader(TransportConnectionReader r) {
		}

		@Override
		public void handleWriter(TransportConnectionWriter w) {
		}
	}

	private static class TestBackoff implements Backoff {

		@Override
		public int getPollingInterval() {
			return 60 * 1000;
		}

		@Override
		public void increment() {
		}

		@Override
		public void reset() {
		}
	}
}
