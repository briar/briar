package org.briarproject.plugins.tcp;

import org.briarproject.api.TransportId;
import org.briarproject.api.plugins.Backoff;
import org.briarproject.api.plugins.duplex.DuplexPluginCallback;
import org.briarproject.api.properties.TransportProperties;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.Executor;

class LanTcpPlugin extends TcpPlugin {

	static final TransportId ID = new TransportId("lan");

	LanTcpPlugin(Executor ioExecutor, Backoff backoff,
			DuplexPluginCallback callback, int maxLatency, int maxIdleTime) {
		super(ioExecutor, backoff, callback, maxLatency, maxIdleTime);
	}

	public TransportId getId() {
		return ID;
	}

	@Override
	protected List<SocketAddress> getLocalSocketAddresses() {
		// Use the same address and port as last time if available
		TransportProperties p = callback.getLocalProperties();
		String oldAddress = p.get("address"), oldPort = p.get("port");
		InetSocketAddress old = parseSocketAddress(oldAddress, oldPort);
		List<SocketAddress> addrs = new LinkedList<SocketAddress>();
		for (InetAddress a : getLocalIpAddresses()) {
			if (isAcceptableAddress(a)) {
				// If this is the old address, try to use the same port
				if (old != null && old.getAddress().equals(a))
					addrs.add(0, new InetSocketAddress(a, old.getPort()));
				addrs.add(new InetSocketAddress(a, 0));
			}
		}
		return addrs;
	}

	private boolean isAcceptableAddress(InetAddress a) {
		// Accept link-local and site-local IPv4 addresses
		boolean ipv4 = a instanceof Inet4Address;
		boolean loop = a.isLoopbackAddress();
		boolean link = a.isLinkLocalAddress();
		boolean site = a.isSiteLocalAddress();
		return ipv4 && !loop && (link || site);
	}

	@Override
	protected boolean isConnectable(InetSocketAddress remote) {
		if (remote.getPort() == 0) return false;
		if (!isAcceptableAddress(remote.getAddress())) return false;
		// Try to determine whether the address is on the same LAN as us
		if (socket == null) return false;
		byte[] localIp = socket.getInetAddress().getAddress();
		byte[] remoteIp = remote.getAddress().getAddress();
		return addressesAreOnSameLan(localIp, remoteIp);
	}

	// Package access for testing
	boolean addressesAreOnSameLan(byte[] localIp, byte[] remoteIp) {
		// 10.0.0.0/8
		if (localIp[0] == 10) return remoteIp[0] == 10;
		// 172.16.0.0/12
		if (localIp[0] == (byte) 172 && (localIp[1] & 0xF0) == 16)
			return remoteIp[0] == (byte) 172 && (remoteIp[1] & 0xF0) == 16;
		// 192.168.0.0/16
		if (localIp[0] == (byte) 192 && localIp[1] == (byte) 168)
			return remoteIp[0] == (byte) 192 && remoteIp[1] == (byte) 168;
		// Unrecognised prefix - may be compatible
		return true;
	}
}
