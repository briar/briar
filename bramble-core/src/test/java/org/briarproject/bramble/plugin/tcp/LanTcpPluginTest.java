package org.briarproject.bramble.plugin.tcp;

import org.briarproject.bramble.api.contact.ContactId;
import org.briarproject.bramble.api.data.BdfList;
import org.briarproject.bramble.api.keyagreement.KeyAgreementListener;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.plugin.Backoff;
import org.briarproject.bramble.api.plugin.duplex.DuplexPlugin;
import org.briarproject.bramble.api.plugin.duplex.DuplexPluginCallback;
import org.briarproject.bramble.api.plugin.duplex.DuplexTransportConnection;
import org.briarproject.bramble.api.properties.TransportProperties;
import org.briarproject.bramble.api.settings.Settings;
import org.briarproject.bramble.plugin.tcp.LanTcpPlugin.LanAddressComparator;
import org.briarproject.bramble.test.BrambleTestCase;
import org.junit.Test;

import java.io.IOException;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Collections;
import java.util.Comparator;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.briarproject.bramble.api.keyagreement.KeyAgreementConstants.COMMIT_LENGTH;
import static org.briarproject.bramble.api.keyagreement.KeyAgreementConstants.TRANSPORT_ID_LAN;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class LanTcpPluginTest extends BrambleTestCase {

	private final Backoff backoff = new TestBackoff();

	@Test
	public void testAddressesAreOnSameLan() {
		LanTcpPlugin plugin = new LanTcpPlugin(null, null, null, 0, 0);
		// Local and remote in 10.0.0.0/8 should return true
		assertTrue(plugin.addressesAreOnSameLan(makeAddress(10, 0, 0, 0),
				makeAddress(10, 255, 255, 255)));
		// Local and remote in 172.16.0.0/12 should return true
		assertTrue(plugin.addressesAreOnSameLan(makeAddress(172, 16, 0, 0),
				makeAddress(172, 31, 255, 255)));
		// Local and remote in 192.168.0.0/16 should return true
		assertTrue(plugin.addressesAreOnSameLan(makeAddress(192, 168, 0, 0),
				makeAddress(192, 168, 255, 255)));
		// Local and remote in 169.254.0.0/16 (link-local) should return true
		assertTrue(plugin.addressesAreOnSameLan(makeAddress(169, 254, 0, 0),
				makeAddress(169, 254, 255, 255)));
		// Local and remote in different recognised prefixes should return false
		assertFalse(plugin.addressesAreOnSameLan(makeAddress(10, 0, 0, 0),
				makeAddress(172, 31, 255, 255)));
		assertFalse(plugin.addressesAreOnSameLan(makeAddress(10, 0, 0, 0),
				makeAddress(192, 168, 255, 255)));
		assertFalse(plugin.addressesAreOnSameLan(makeAddress(172, 16, 0, 0),
				makeAddress(10, 255, 255, 255)));
		assertFalse(plugin.addressesAreOnSameLan(makeAddress(172, 16, 0, 0),
				makeAddress(192, 168, 255, 255)));
		assertFalse(plugin.addressesAreOnSameLan(makeAddress(192, 168, 0, 0),
				makeAddress(10, 255, 255, 255)));
		assertFalse(plugin.addressesAreOnSameLan(makeAddress(192, 168, 0, 0),
				makeAddress(172, 31, 255, 255)));
		// Remote prefix unrecognised should return false
		assertFalse(plugin.addressesAreOnSameLan(makeAddress(10, 0, 0, 0),
				makeAddress(1, 2, 3, 4)));
		assertFalse(plugin.addressesAreOnSameLan(makeAddress(172, 16, 0, 0),
				makeAddress(1, 2, 3, 4)));
		assertFalse(plugin.addressesAreOnSameLan(makeAddress(192, 168, 0, 0),
				makeAddress(1, 2, 3, 4)));
		// Both prefixes unrecognised should return true (could be link-local)
		assertTrue(plugin.addressesAreOnSameLan(makeAddress(1, 2, 3, 4),
				makeAddress(5, 6, 7, 8)));
	}

	private byte[] makeAddress(int... parts) {
		byte[] b = new byte[parts.length];
		for (int i = 0; i < parts.length; i++) b[i] = (byte) parts[i];
		return b;
	}

	@Test
	public void testIncomingConnection() throws Exception {
		if (!systemHasLocalIpv4Address()) {
			System.err.println("WARNING: Skipping test, no local IPv4 address");
			return;
		}
		Callback callback = new Callback();
		Executor executor = Executors.newCachedThreadPool();
		DuplexPlugin plugin = new LanTcpPlugin(executor, backoff, callback,
				0, 0);
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
		if (!systemHasLocalIpv4Address()) {
			System.err.println("WARNING: Skipping test, no local IPv4 address");
			return;
		}
		Callback callback = new Callback();
		Executor executor = Executors.newCachedThreadPool();
		DuplexPlugin plugin = new LanTcpPlugin(executor, backoff, callback,
				0, 0);
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
		if (!systemHasLocalIpv4Address()) {
			System.err.println("WARNING: Skipping test, no local IPv4 address");
			return;
		}
		Callback callback = new Callback();
		Executor executor = Executors.newCachedThreadPool();
		DuplexPlugin plugin = new LanTcpPlugin(executor, backoff, callback,
				0, 0);
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
		if (!systemHasLocalIpv4Address()) {
			System.err.println("WARNING: Skipping test, no local IPv4 address");
			return;
		}
		Callback callback = new Callback();
		Executor executor = Executors.newCachedThreadPool();
		DuplexPlugin plugin = new LanTcpPlugin(executor, backoff, callback,
				0, 0);
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

	@Test
	public void testComparatorPrefersNonZeroPorts() {
		Comparator<InetSocketAddress> comparator = new LanAddressComparator();
		InetSocketAddress nonZero = new InetSocketAddress("1.2.3.4", 1234);
		InetSocketAddress zero = new InetSocketAddress("1.2.3.4", 0);

		assertEquals(0, comparator.compare(nonZero, nonZero));
		assertTrue(comparator.compare(nonZero, zero) < 0);

		assertTrue(comparator.compare(zero, nonZero) > 0);
		assertEquals(0, comparator.compare(zero, zero));
	}

	@Test
	public void testComparatorPrefersLongerPrefixes() {
		Comparator<InetSocketAddress> comparator = new LanAddressComparator();
		InetSocketAddress prefix192 = new InetSocketAddress("192.168.0.1", 0);
		InetSocketAddress prefix172 = new InetSocketAddress("172.16.0.1", 0);
		InetSocketAddress prefix10 = new InetSocketAddress("10.0.0.1", 0);

		assertEquals(0, comparator.compare(prefix192, prefix192));
		assertTrue(comparator.compare(prefix192, prefix172) < 0);
		assertTrue(comparator.compare(prefix192, prefix10) < 0);

		assertTrue(comparator.compare(prefix172, prefix192) > 0);
		assertEquals(0, comparator.compare(prefix172, prefix172));
		assertTrue(comparator.compare(prefix172, prefix10) < 0);

		assertTrue(comparator.compare(prefix10, prefix192) > 0);
		assertTrue(comparator.compare(prefix10, prefix172) > 0);
		assertEquals(0, comparator.compare(prefix10, prefix10));
	}

	@Test
	public void testComparatorPrefersSiteLocalToLinkLocal() {
		Comparator<InetSocketAddress> comparator = new LanAddressComparator();
		InetSocketAddress prefix192 = new InetSocketAddress("192.168.0.1", 0);
		InetSocketAddress prefix172 = new InetSocketAddress("172.16.0.1", 0);
		InetSocketAddress prefix10 = new InetSocketAddress("10.0.0.1", 0);
		InetSocketAddress linkLocal = new InetSocketAddress("169.254.0.1", 0);

		assertTrue(comparator.compare(prefix192, linkLocal) < 0);
		assertTrue(comparator.compare(prefix172, linkLocal) < 0);
		assertTrue(comparator.compare(prefix10, linkLocal) < 0);

		assertTrue(comparator.compare(linkLocal, prefix192) > 0);
		assertTrue(comparator.compare(linkLocal, prefix172) > 0);
		assertTrue(comparator.compare(linkLocal, prefix10) > 0);
		assertEquals(0, comparator.compare(linkLocal, linkLocal));
	}

	private boolean systemHasLocalIpv4Address() throws Exception {
		for (NetworkInterface i : Collections.list(
				NetworkInterface.getNetworkInterfaces())) {
			for (InetAddress a : Collections.list(i.getInetAddresses())) {
				if (a instanceof Inet4Address)
					return a.isLinkLocalAddress() || a.isSiteLocalAddress();
			}
		}
		return false;
	}

	@NotNullByDefault
	private static class Callback implements DuplexPluginCallback {

		private final CountDownLatch propertiesLatch = new CountDownLatch(1);
		private final CountDownLatch connectionsLatch = new CountDownLatch(1);
		private final TransportProperties local = new TransportProperties();

		@Override
		public Settings getSettings() {
			return new Settings();
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
		public void incomingConnectionCreated(DuplexTransportConnection d) {
			connectionsLatch.countDown();
		}

		@Override
		public void outgoingConnectionCreated(ContactId c,
				DuplexTransportConnection d) {
		}

		@Override
		public void transportEnabled() {
		}

		@Override
		public void transportDisabled() {
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
