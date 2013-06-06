package net.sf.briar.plugins.tcp;

import static java.util.logging.Level.INFO;
import static java.util.logging.Level.WARNING;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.MulticastSocket;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Logger;

import net.sf.briar.api.TransportId;
import net.sf.briar.api.TransportProperties;
import net.sf.briar.api.clock.Clock;
import net.sf.briar.api.crypto.PseudoRandom;
import net.sf.briar.api.plugins.duplex.DuplexPluginCallback;
import net.sf.briar.api.plugins.duplex.DuplexTransportConnection;
import net.sf.briar.util.ByteUtils;
import net.sf.briar.util.StringUtils;

/** A socket plugin that supports exchanging invitations over a LAN. */
class LanTcpPlugin extends TcpPlugin {

	static final byte[] TRANSPORT_ID =
			StringUtils.fromHexString("0d79357fd7f74d66c2f6f6ad0f7fff81"
					+ "d21c53a43b90b0507ed0683872d8e2fc"
					+ "5a88e8f953638228dc26669639757bbf");
	static final TransportId ID = new TransportId(TRANSPORT_ID);

	private static final Logger LOG =
			Logger.getLogger(LanTcpPlugin.class.getName());
	private static final int MULTICAST_INTERVAL = 1000; // 1 second

	private final Clock clock;

	LanTcpPlugin(Executor pluginExecutor, Clock clock,
			DuplexPluginCallback callback, int maxFrameLength, long maxLatency,
			long pollingInterval) {
		super(pluginExecutor, callback, maxFrameLength, maxLatency,
				pollingInterval);
		this.clock = clock;
	}

	public TransportId getId() {
		return ID;
	}

	public String getName() {
		return "LAN_TCP_PLUGIN_NAME";
	}

	@Override
	protected List<SocketAddress> getLocalSocketAddresses() {
		List<SocketAddress> addrs = new ArrayList<SocketAddress>();
		// Prefer a previously used address and port if available
		TransportProperties p = callback.getLocalProperties();
		String addrString = p.get("address");
		String portString = p.get("port");
		InetAddress addr = null;
		if(!StringUtils.isNullOrEmpty(addrString) &&
				!StringUtils.isNullOrEmpty(portString)) {
			try {
				addr = InetAddress.getByName(addrString);
				int port = Integer.parseInt(portString);
				addrs.add(new InetSocketAddress(addr, port));
				addrs.add(new InetSocketAddress(addr, 0));
			} catch(NumberFormatException e) {
				if(LOG.isLoggable(WARNING)) LOG.log(WARNING, e.toString(), e);
			} catch(UnknownHostException e) {
				if(LOG.isLoggable(WARNING)) LOG.log(WARNING, e.toString(), e);
			}
		}
		List<NetworkInterface> ifaces;
		try {
			ifaces = Collections.list(NetworkInterface.getNetworkInterfaces());
		} catch(SocketException e) {
			if(LOG.isLoggable(WARNING)) LOG.log(WARNING, e.toString(), e);
			return addrs;
		}
		// Prefer interfaces with link-local or site-local addresses
		for(NetworkInterface iface : ifaces) {
			for(InetAddress a : Collections.list(iface.getInetAddresses())) {
				if(addr != null && a.equals(addr)) continue;
				if(a instanceof Inet6Address) continue;
				if(a.isLoopbackAddress()) continue;
				boolean link = a.isLinkLocalAddress();
				boolean site = a.isSiteLocalAddress();
				if(link || site) addrs.add(new InetSocketAddress(a, 0));
			}
		}
		// Accept interfaces without link-local or site-local addresses
		for(NetworkInterface iface : ifaces) {
			for(InetAddress a : Collections.list(iface.getInetAddresses())) {
				if(addr != null && a.equals(addr)) continue;
				if(a instanceof Inet6Address) continue;
				if(a.isLoopbackAddress()) continue;
				boolean link = a.isLinkLocalAddress();
				boolean site = a.isSiteLocalAddress();
				if(!link && !site) addrs.add(new InetSocketAddress(a, 0));
			}
		}
		return addrs;
	}

	public boolean supportsInvitations() {
		return true;
	}

	public DuplexTransportConnection sendInvitation(PseudoRandom r,
			long timeout) {
		if(!running) return null;
		// Use the invitation codes to generate the group address and port
		InetSocketAddress group = chooseMulticastGroup(r);
		// Bind a multicast socket for sending and receiving packets
		InetAddress iface = null;
		MulticastSocket ms = null;
		try {
			iface = chooseInvitationInterface();
			if(iface == null) return null;
			ms = new MulticastSocket(group.getPort());
			ms.setInterface(iface);
			ms.joinGroup(group.getAddress());
		} catch(IOException e) {
			if(LOG.isLoggable(WARNING)) LOG.log(WARNING, e.toString(), e);
			if(ms != null) tryToClose(ms, group.getAddress());
			return null;
		}
		// Bind a server socket for receiving invitation connections
		ServerSocket ss = null;
		try {
			ss = new ServerSocket();
			ss.bind(new InetSocketAddress(iface, 0));
		} catch(IOException e) {
			if(LOG.isLoggable(WARNING)) LOG.log(WARNING, e.toString(), e);
			if(ss != null) tryToClose(ss);
			return null;
		}
		// Start the listener threads
		SocketReceiver receiver = new SocketReceiver();
		new MulticastListenerThread(receiver, ms, iface).start();
		new TcpListenerThread(receiver, ss).start();
		// Send packets until a connection is made or we run out of time
		byte[] buffer = new byte[2];
		ByteUtils.writeUint16(ss.getLocalPort(), buffer, 0);
		DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
		packet.setAddress(group.getAddress());
		packet.setPort(group.getPort());
		long now = clock.currentTimeMillis();
		long end = now + timeout;
		try {
			while(now < end && running) {
				// Send a packet
				if(LOG.isLoggable(INFO)) LOG.info("Sending multicast packet");
				ms.send(packet);
				// Wait for an incoming or outgoing connection
				try {
					Socket s = receiver.waitForSocket(MULTICAST_INTERVAL);
					if(s != null) return new TcpTransportConnection(this, s);
				} catch(InterruptedException e) {
					if(LOG.isLoggable(INFO))
						LOG.info("Interrupted while exchanging invitations");
					Thread.currentThread().interrupt();
					return null;
				}
				now = clock.currentTimeMillis();
			}
		} catch(IOException e) {
			if(LOG.isLoggable(WARNING)) LOG.log(WARNING, e.toString(), e);
		} finally {
			// Closing the sockets will terminate the listener threads
			tryToClose(ms, group.getAddress());
			tryToClose(ss);
		}
		return null;
	}

	private InetSocketAddress chooseMulticastGroup(PseudoRandom r) {
		byte[] b = r.nextBytes(5);
		// The group address is 239.random.random.random, excluding 0 and 255
		byte[] group = new byte[4];
		group[0] = (byte) 239;
		group[1] = legalAddressByte(b[0]);
		group[2] = legalAddressByte(b[1]);
		group[3] = legalAddressByte(b[2]);
		// The port is random in the range 32768 - 65535, inclusive
		int port = ByteUtils.readUint16(b, 3);
		if(port < 32768) port += 32768;
		InetAddress address;
		try {
			address = InetAddress.getByAddress(group);
		} catch(UnknownHostException badAddressLength) {
			throw new RuntimeException(badAddressLength);
		}
		return new InetSocketAddress(address, port);
	}

	private byte legalAddressByte(byte b) {
		if(b == 0) return 1;
		if(b == (byte) 255) return (byte) 254;
		return b;
	}

	private InetAddress chooseInvitationInterface() throws IOException {
		List<NetworkInterface> ifaces =
				Collections.list(NetworkInterface.getNetworkInterfaces());
		// Prefer an interface with a link-local or site-local address
		for(NetworkInterface iface : ifaces) {
			for(InetAddress addr : Collections.list(iface.getInetAddresses())) {
				if(addr.isLoopbackAddress()) continue;
				boolean link = addr.isLinkLocalAddress();
				boolean site = addr.isSiteLocalAddress();
				if(link || site) return addr;
			}
		}
		// Accept an interface without a link-local or site-local address
		for(NetworkInterface iface : ifaces) {
			for(InetAddress addr : Collections.list(iface.getInetAddresses())) {
				if(!addr.isLoopbackAddress()) return addr;
			}
		}
		// No suitable interfaces
		return null;
	}

	private void tryToClose(MulticastSocket ms, InetAddress addr) {
		try {
			ms.leaveGroup(addr);
		} catch(IOException e) {
			if(LOG.isLoggable(WARNING)) LOG.log(WARNING, e.toString(), e);
		}
		ms.close();
	}

	public DuplexTransportConnection acceptInvitation(PseudoRandom r,
			long timeout) {
		// FIXME
		return sendInvitation(r, timeout);
	}

	private static class SocketReceiver {

		private final CountDownLatch latch = new CountDownLatch(1);
		private final AtomicReference<Socket> socket =
				new AtomicReference<Socket>();

		private boolean setSocket(Socket s) {
			if(socket.compareAndSet(null, s)) {
				latch.countDown();
				return true;
			}
			return false;
		}

		private Socket waitForSocket(long timeout) throws InterruptedException {
			latch.await(timeout, TimeUnit.MILLISECONDS);
			return socket.get();
		}
	}

	private class MulticastListenerThread extends Thread {

		private final SocketReceiver receiver;
		private final MulticastSocket multicastSocket;
		private final InetAddress localAddress;

		private MulticastListenerThread(SocketReceiver receiver,
				MulticastSocket multicastSocket, InetAddress localAddress) {
			this.receiver = receiver;
			this.multicastSocket = multicastSocket;
			this.localAddress = localAddress;
		}

		@Override
		public void run() {
			if(LOG.isLoggable(INFO))
				LOG.info("Listening for multicast packets");
			// Listen until a valid packet is received or the socket is closed
			byte[] buffer = new byte[2];
			DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
			try {
				while(running) {
					multicastSocket.receive(packet);
					if(LOG.isLoggable(INFO))
						LOG.info("Received multicast packet");
					parseAndConnectBack(packet);
				}
			} catch(IOException e) {
				// This is expected when the socket is closed
				if(LOG.isLoggable(INFO)) LOG.log(INFO, e.toString(), e);
			}
		}

		private void parseAndConnectBack(DatagramPacket packet) {
			InetAddress addr = packet.getAddress();
			if(addr.equals(localAddress)) {
				if(LOG.isLoggable(INFO)) LOG.info("Ignoring own packet");
				return;
			}
			byte[] b = packet.getData();
			int off = packet.getOffset();
			int len = packet.getLength();
			if(len != 2) {
				if(LOG.isLoggable(INFO)) LOG.info("Invalid length: " + len);
				return;
			}
			int port = ByteUtils.readUint16(b, off);
			if(port < 32768 || port >= 65536) {
				if(LOG.isLoggable(INFO)) LOG.info("Invalid port: " + port);
				return;
			}
			if(LOG.isLoggable(INFO))
				LOG.info("Packet from " + getHostAddress(addr) + ":" + port);
			try {
				// Connect back on the advertised TCP port
				Socket s = new Socket(addr, port);
				if(LOG.isLoggable(INFO)) LOG.info("Outgoing connection");
				if(!receiver.setSocket(s)) {
					if(LOG.isLoggable(INFO))
						LOG.info("Closing redundant connection");
					s.close();
				}
			} catch(IOException e) {
				if(LOG.isLoggable(WARNING)) LOG.log(WARNING, e.toString(), e);
			}
		}
	}

	private class TcpListenerThread extends Thread {

		private final SocketReceiver receiver;
		private final ServerSocket serverSocket;

		private TcpListenerThread(SocketReceiver receiver,
				ServerSocket serverSocket) {
			this.receiver = receiver;
			this.serverSocket = serverSocket;
		}

		@Override
		public void run() {
			if(LOG.isLoggable(INFO))
				LOG.info("Listening for invitation connections");
			// Listen until a connection is received or the socket is closed
			try {
				Socket s = serverSocket.accept();
				if(LOG.isLoggable(INFO)) LOG.info("Incoming connection");
				if(!receiver.setSocket(s)) {
					if(LOG.isLoggable(INFO))
						LOG.info("Closing redundant connection");
					s.close();
				}
			} catch(IOException e) {
				// This is expected when the socket is closed
				if(LOG.isLoggable(INFO)) LOG.log(INFO, e.toString(), e);
			}
		}
	}
}