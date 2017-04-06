package org.briarproject.bramble.plugin.tcp;

import org.briarproject.bramble.api.FormatException;
import org.briarproject.bramble.api.contact.ContactId;
import org.briarproject.bramble.api.data.BdfList;
import org.briarproject.bramble.api.keyagreement.KeyAgreementConnection;
import org.briarproject.bramble.api.keyagreement.KeyAgreementListener;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.plugin.Backoff;
import org.briarproject.bramble.api.plugin.TransportId;
import org.briarproject.bramble.api.plugin.duplex.DuplexPluginCallback;
import org.briarproject.bramble.api.plugin.duplex.DuplexTransportConnection;
import org.briarproject.bramble.api.properties.TransportProperties;
import org.briarproject.bramble.api.settings.Settings;
import org.briarproject.bramble.util.StringUtils;

import java.io.IOException;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.Executor;
import java.util.logging.Logger;

import static java.util.logging.Level.INFO;
import static java.util.logging.Level.WARNING;
import static org.briarproject.bramble.api.keyagreement.KeyAgreementConstants.TRANSPORT_ID_LAN;
import static org.briarproject.bramble.api.plugin.LanTcpConstants.ID;
import static org.briarproject.bramble.api.plugin.LanTcpConstants.PREF_LAN_IP_PORTS;
import static org.briarproject.bramble.util.ByteUtils.MAX_16_BIT_UNSIGNED;
import static org.briarproject.bramble.util.PrivacyUtils.scrubSocketAddress;

@NotNullByDefault
class LanTcpPlugin extends TcpPlugin {

	private static final Logger LOG =
			Logger.getLogger(LanTcpPlugin.class.getName());

	private static final int MAX_ADDRESSES = 4;
	private static final String PROP_IP_PORTS = "ipPorts";
	private static final String SEPARATOR = ",";

	LanTcpPlugin(Executor ioExecutor, Backoff backoff,
			DuplexPluginCallback callback, int maxLatency, int maxIdleTime) {
		super(ioExecutor, backoff, callback, maxLatency, maxIdleTime);
	}

	@Override
	public TransportId getId() {
		return ID;
	}

	@Override
	protected List<InetSocketAddress> getLocalSocketAddresses() {
		// Use the same address and port as last time if available
		TransportProperties p = callback.getLocalProperties();
		String oldIpPorts = p.get(PROP_IP_PORTS);
		List<InetSocketAddress> olds = parseSocketAddresses(oldIpPorts);
		List<InetSocketAddress> locals = new LinkedList<InetSocketAddress>();
		for (InetAddress local : getLocalIpAddresses()) {
			if (isAcceptableAddress(local)) {
				// If this is the old address, try to use the same port
				for (InetSocketAddress old : olds) {
					if (old.getAddress().equals(local)) {
						int port = old.getPort();
						locals.add(0, new InetSocketAddress(local, port));
					}
				}
				locals.add(new InetSocketAddress(local, 0));
			}
		}
		return locals;
	}

	private List<InetSocketAddress> parseSocketAddresses(String ipPorts) {
		if (StringUtils.isNullOrEmpty(ipPorts)) return Collections.emptyList();
		String[] split = ipPorts.split(SEPARATOR);
		List<InetSocketAddress> addresses = new ArrayList<InetSocketAddress>();
		for (String ipPort : split) {
			InetSocketAddress a = parseSocketAddress(ipPort);
			if (a != null) addresses.add(a);
		}
		return addresses;
	}

	@Override
	protected void setLocalSocketAddress(InetSocketAddress a) {
		String ipPort = getIpPortString(a);
		// Get the list of recently used addresses
		String setting = callback.getSettings().get(PREF_LAN_IP_PORTS);
		List<String> recent = new ArrayList<String>();
		if (!StringUtils.isNullOrEmpty(setting))
			Collections.addAll(recent, setting.split(SEPARATOR));
		// Is the address already in the list?
		if (recent.remove(ipPort)) {
			// Move the address to the start of the list
			recent.add(0, ipPort);
			setting = StringUtils.join(recent, SEPARATOR);
		} else {
			// Add the address to the start of the list
			recent.add(0, ipPort);
			// Drop the least recently used address if the list is full
			if (recent.size() > MAX_ADDRESSES)
				recent = recent.subList(0, MAX_ADDRESSES);
			setting = StringUtils.join(recent, SEPARATOR);
			// Update the list of addresses shared with contacts
			List<String> shared = new ArrayList<String>(recent);
			Collections.sort(shared);
			String property = StringUtils.join(shared, SEPARATOR);
			TransportProperties properties = new TransportProperties();
			properties.put(PROP_IP_PORTS, property);
			callback.mergeLocalProperties(properties);
		}
		// Save the setting
		Settings settings = new Settings();
		settings.put(PREF_LAN_IP_PORTS, setting);
		callback.mergeSettings(settings);
	}

	@Override
	protected List<InetSocketAddress> getRemoteSocketAddresses(ContactId c) {
		TransportProperties p = callback.getRemoteProperties().get(c);
		if (p == null) return Collections.emptyList();
		return parseSocketAddresses(p.get(PROP_IP_PORTS));
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

	@Override
	public boolean supportsKeyAgreement() {
		return true;
	}

	@Override
	public KeyAgreementListener createKeyAgreementListener(byte[] commitment) {
		ServerSocket ss = null;
		for (InetSocketAddress addr : getLocalSocketAddresses()) {
			// Don't try to reuse the same port we use for contact connections
			addr = new InetSocketAddress(addr.getAddress(), 0);
			try {
				ss = new ServerSocket();
				ss.bind(addr);
				break;
			} catch (IOException e) {
				if (LOG.isLoggable(INFO))
					LOG.info("Failed to bind " + scrubSocketAddress(addr));
				tryToClose(ss);
			}
		}
		if (ss == null || !ss.isBound()) {
			LOG.info("Could not bind server socket for key agreement");
			return null;
		}
		BdfList descriptor = new BdfList();
		descriptor.add(TRANSPORT_ID_LAN);
		InetSocketAddress local =
				(InetSocketAddress) ss.getLocalSocketAddress();
		descriptor.add(local.getAddress().getAddress());
		descriptor.add(local.getPort());
		return new LanKeyAgreementListener(descriptor, ss);
	}

	@Override
	public DuplexTransportConnection createKeyAgreementConnection(
			byte[] commitment, BdfList descriptor, long timeout) {
		if (!isRunning()) return null;
		InetSocketAddress remote;
		try {
			remote = parseSocketAddress(descriptor);
		} catch (FormatException e) {
			LOG.info("Invalid IP/port in key agreement descriptor");
			return null;
		}
		if (!isConnectable(remote)) {
			if (LOG.isLoggable(INFO)) {
				SocketAddress local = socket.getLocalSocketAddress();
				LOG.info(scrubSocketAddress(remote) +
						" is not connectable from " +
						scrubSocketAddress(local));
			}
			return null;
		}
		Socket s = new Socket();
		try {
			if (LOG.isLoggable(INFO))
				LOG.info("Connecting to " + scrubSocketAddress(remote));
			s.connect(remote);
			s.setSoTimeout(socketTimeout);
			if (LOG.isLoggable(INFO))
				LOG.info("Connected to " + scrubSocketAddress(remote));
			return new TcpTransportConnection(this, s);
		} catch (IOException e) {
			if (LOG.isLoggable(INFO))
				LOG.info("Could not connect to " + scrubSocketAddress(remote));
			return null;
		}
	}

	private InetSocketAddress parseSocketAddress(BdfList descriptor)
			throws FormatException {
		byte[] address = descriptor.getRaw(1);
		int port = descriptor.getLong(2).intValue();
		if (port < 1 || port > MAX_16_BIT_UNSIGNED) throw new FormatException();
		try {
			InetAddress addr = InetAddress.getByAddress(address);
			return new InetSocketAddress(addr, port);
		} catch (UnknownHostException e) {
			// Invalid address length
			throw new FormatException();
		}
	}

	private class LanKeyAgreementListener extends KeyAgreementListener {

		private final ServerSocket ss;

		private LanKeyAgreementListener(BdfList descriptor,
				ServerSocket ss) {
			super(descriptor);
			this.ss = ss;
		}

		@Override
		public Callable<KeyAgreementConnection> listen() {
			return new Callable<KeyAgreementConnection>() {
				@Override
				public KeyAgreementConnection call() throws IOException {
					Socket s = ss.accept();
					if (LOG.isLoggable(INFO))
						LOG.info(ID.getString() + ": Incoming connection");
					return new KeyAgreementConnection(
							new TcpTransportConnection(LanTcpPlugin.this, s),
							ID);
				}
			};
		}

		@Override
		public void close() {
			try {
				ss.close();
			} catch (IOException e) {
				if (LOG.isLoggable(WARNING)) LOG.log(WARNING, e.toString(), e);
			}
		}
	}
}
