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
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Executor;
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

	private final Clock clock;

	LanTcpPlugin(Executor pluginExecutor, Clock clock,
			DuplexPluginCallback callback, long maxLatency,
			long pollingInterval) {
		super(pluginExecutor, callback, maxLatency, pollingInterval);
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
				int port = Integer.valueOf(portString);
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
		// Use the invitation code to choose the group address and port
		InetSocketAddress mcast = chooseMulticastGroup(r);
		// Bind a multicast socket for receiving packets
		MulticastSocket ms = null;
		try {
			InetAddress iface = chooseInterface();
			if(iface == null) return null;
			ms = new MulticastSocket(mcast.getPort());
			ms.setInterface(iface);
			ms.joinGroup(mcast.getAddress());
		} catch(IOException e) {
			if(LOG.isLoggable(WARNING)) LOG.log(WARNING, e.toString(), e);
			if(ms != null) tryToClose(ms, mcast.getAddress());
			return null;
		}
		// Listen until a valid packet is received or the timeout occurs
		byte[] buffer = new byte[2];
		DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
		long now = clock.currentTimeMillis();
		long end = now + timeout;
		try {
			while(now < end) {
				try {
					ms.setSoTimeout((int) (end - now));
					ms.receive(packet);
					byte[] b = packet.getData();
					int off = packet.getOffset();
					int len = packet.getLength();
					int port = parsePacket(b, off, len);
					if(port >= 32768 && port < 65536) {
						try {
							// Connect back on the advertised TCP port
							Socket s = new Socket(packet.getAddress(), port);
							return new TcpTransportConnection(s, maxLatency);
						} catch(IOException e) {
							if(LOG.isLoggable(WARNING))
								LOG.log(WARNING, e.toString(), e);
						}
					}
				} catch(SocketTimeoutException e) {
					break;
				}
				now = clock.currentTimeMillis();
				if(!running) return null;
			}
			if(LOG.isLoggable(INFO))
				LOG.info("Timeout while sending invitation");
		} catch(IOException e) {
			if(LOG.isLoggable(WARNING)) LOG.log(WARNING, e.toString(), e);
		} finally {
			tryToClose(ms, mcast.getAddress());
		}
		return null;
	}

	private InetAddress chooseInterface() throws IOException {
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

	private int parsePacket(byte[] b, int off, int len) {
		if(len != 2) return 0;
		return ByteUtils.readUint16(b, off);
	}

	public DuplexTransportConnection acceptInvitation(PseudoRandom r,
			long timeout) {
		if(!running) return null;
		// Use the invitation code to choose the group address and port
		InetSocketAddress mcast = chooseMulticastGroup(r);
		// Bind a TCP socket for receiving connections
		ServerSocket ss = null;
		try {
			InetAddress iface = chooseInterface();
			if(iface == null) return null;
			ss = new ServerSocket();
			ss.bind(new InetSocketAddress(iface, 0));
		} catch(IOException e) {
			if(LOG.isLoggable(WARNING)) LOG.log(WARNING, e.toString(), e);
			if(ss != null) tryToClose(ss);
			return null;
		}
		// Bind a multicast socket for sending packets
		MulticastSocket ms = null;
		try {
			InetAddress iface = chooseInterface();
			if(iface == null) return null;
			ms = new MulticastSocket();
			ms.setInterface(iface);
		} catch(IOException e) {
			if(LOG.isLoggable(WARNING)) LOG.log(WARNING, e.toString(), e);
			if(ms != null) ms.close();
			tryToClose(ss);
			return null;
		}
		// Send packets until a connection is received or the timeout expires
		byte[] buffer = new byte[2];
		ByteUtils.writeUint16(ss.getLocalPort(), buffer, 0);
		DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
		packet.setAddress(mcast.getAddress());
		packet.setPort(mcast.getPort());
		long now = clock.currentTimeMillis();
		long end = now + timeout;
		long interval = 1000;
		long nextPacket = now + 1;
		try {
			while(now < end) {
				try {
					int wait = (int) (Math.min(end, nextPacket) - now);
					ss.setSoTimeout(wait < 1 ? 1 : wait);
					Socket s = ss.accept();
					return new TcpTransportConnection(s, maxLatency);
				} catch(SocketTimeoutException e) {
					now = clock.currentTimeMillis();
					if(now < end) {
						ms.send(packet);
						now = clock.currentTimeMillis();
						nextPacket = now + interval;
						interval += 1000;
					}
				}
				if(!running) return null;
			}
			if(LOG.isLoggable(INFO))
				LOG.info("Timeout while accepting invitation");
		} catch(IOException e) {
			if(LOG.isLoggable(WARNING)) LOG.log(WARNING, e.toString(), e);
		} finally {
			ms.close();
			tryToClose(ss);
		}
		return null;
	}
}
