package net.sf.briar.plugins.socket;

import static java.util.logging.Level.INFO;
import static java.util.logging.Level.WARNING;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.MulticastSocket;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.concurrent.Executor;
import java.util.logging.Logger;

import net.sf.briar.api.crypto.PseudoRandom;
import net.sf.briar.api.plugins.PluginExecutor;
import net.sf.briar.api.plugins.duplex.DuplexPluginCallback;
import net.sf.briar.api.plugins.duplex.DuplexTransportConnection;
import net.sf.briar.util.ByteUtils;

/** A socket plugin that supports exchanging invitations over a LAN. */
class LanSocketPlugin extends SimpleSocketPlugin {

	private static final Logger LOG =
			Logger.getLogger(LanSocketPlugin.class.getName());

	LanSocketPlugin(@PluginExecutor Executor pluginExecutor,
			DuplexPluginCallback callback, long pollingInterval) {
		super(pluginExecutor, callback, pollingInterval);
	}

	@Override
	public boolean supportsInvitations() {
		return true;
	}

	@Override
	public DuplexTransportConnection sendInvitation(PseudoRandom r,
			long timeout) {
		synchronized(this) {
			if(!running) return null;
		}
		// Use the invitation code to choose the group address and port
		InetSocketAddress mcast = chooseMulticastGroup(r);
		// Bind a multicast socket for receiving packets
		MulticastSocket ms = null;
		try {
			InetAddress iface = chooseInterface(true);
			ms = new MulticastSocket(mcast.getPort());
			ms.setInterface(iface);
			ms.joinGroup(mcast.getAddress());
		} catch(IOException e) {
			if(LOG.isLoggable(WARNING)) LOG.warning(e.toString());
			if(ms != null) tryToClose(ms, mcast.getAddress());
			return null;
		}
		// Listen until a valid packet is received or the timeout occurs
		byte[] buffer = new byte[2];
		DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
		long now = System.currentTimeMillis();
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
							return new SocketTransportConnection(s);
						} catch(IOException e) {
							if(LOG.isLoggable(WARNING))
								LOG.warning(e.toString());
						}
					}
				} catch(SocketTimeoutException e) {
					break;
				}
				now = System.currentTimeMillis();
				synchronized(this) {
					if(!running) return null;
				}
			}
			if(LOG.isLoggable(INFO))
				LOG.info("Timeout while sending invitation");
		} catch(IOException e) {
			if(LOG.isLoggable(WARNING)) LOG.warning(e.toString());
		} finally {
			tryToClose(ms, mcast.getAddress());
		}
		return null;
	}

	private void tryToClose(MulticastSocket ms, InetAddress addr) {
		try {
			ms.leaveGroup(addr);
		} catch(IOException e) {
			if(LOG.isLoggable(WARNING)) LOG.warning(e.toString());
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

	@Override
	public DuplexTransportConnection acceptInvitation(PseudoRandom r,
			long timeout) {
		synchronized(this) {
			if(!running) return null;
		}
		// Use the invitation code to choose the group address and port
		InetSocketAddress mcast = chooseMulticastGroup(r);
		// Bind a TCP socket for receiving connections
		ServerSocket ss = null;
		try {
			InetAddress iface = chooseInterface(true);
			ss = new ServerSocket();
			ss.bind(new InetSocketAddress(iface, 0));
		} catch(IOException e) {
			if(LOG.isLoggable(WARNING)) LOG.warning(e.toString());
			if(ss != null) tryToClose(ss);
			return null;
		}
		// Bind a multicast socket for sending packets
		MulticastSocket ms = null;
		try {
			InetAddress iface = chooseInterface(true);
			ms = new MulticastSocket();
			ms.setInterface(iface);
		} catch(IOException e) {
			if(LOG.isLoggable(WARNING)) LOG.warning(e.toString());
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
		long now = System.currentTimeMillis();
		long end = now + timeout;
		long interval = 1000;
		long nextPacket = now + 1;
		try {
			while(now < end) {
				try {
					int wait = (int) (Math.min(end, nextPacket) - now);
					ss.setSoTimeout(wait < 1 ? 1 : wait);
					return new SocketTransportConnection(ss.accept());
				} catch(SocketTimeoutException e) {
					now = System.currentTimeMillis();
					if(now < end) {
						ms.send(packet);
						now = System.currentTimeMillis();
						nextPacket = now + interval;
						interval += 1000;
					}
				}
				synchronized(this) {
					if(!running) return null;
				}
			}
			if(LOG.isLoggable(INFO))
				LOG.info("Timeout while accepting invitation");
		} catch(IOException e) {
			if(LOG.isLoggable(WARNING)) LOG.warning(e.toString());
		} finally {
			ms.close();
			tryToClose(ss);
		}
		return null;
	}

	private void tryToClose(ServerSocket ss) {
		try {
			ss.close();
		} catch(IOException e) {
			if(LOG.isLoggable(WARNING)) LOG.warning(e.toString());
		}
	}
}
