package org.briarproject.bramble.plugin.tcp;

import org.briarproject.bramble.api.contact.ContactId;
import org.briarproject.bramble.api.data.BdfList;
import org.briarproject.bramble.api.keyagreement.KeyAgreementListener;
import org.briarproject.bramble.api.nullsafety.MethodsNotNullByDefault;
import org.briarproject.bramble.api.nullsafety.ParametersNotNullByDefault;
import org.briarproject.bramble.api.plugin.Backoff;
import org.briarproject.bramble.api.plugin.duplex.DuplexPlugin;
import org.briarproject.bramble.api.plugin.duplex.DuplexPluginCallback;
import org.briarproject.bramble.api.plugin.duplex.DuplexTransportConnection;
import org.briarproject.bramble.api.properties.TransportProperties;
import org.briarproject.bramble.util.StringUtils;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;
import java.util.regex.Pattern;

import javax.annotation.Nullable;

import static java.util.logging.Level.INFO;
import static java.util.logging.Level.WARNING;
import static org.briarproject.bramble.util.PrivacyUtils.scrubSocketAddress;

@MethodsNotNullByDefault
@ParametersNotNullByDefault
abstract class TcpPlugin implements DuplexPlugin {

	private static final Pattern DOTTED_QUAD =
			Pattern.compile("^\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}$");
	private static final Logger LOG =
			Logger.getLogger(TcpPlugin.class.getName());

	protected final Executor ioExecutor;
	protected final Backoff backoff;
	protected final DuplexPluginCallback callback;
	protected final int maxLatency, maxIdleTime, socketTimeout;
	protected final AtomicBoolean used = new AtomicBoolean(false);

	protected volatile boolean running = false;
	protected volatile ServerSocket socket = null;

	/**
	 * Returns zero or more socket addresses on which the plugin should listen,
	 * in order of preference. At most one of the addresses will be bound.
	 */
	protected abstract List<InetSocketAddress> getLocalSocketAddresses();

	/**
	 * Adds the address on which the plugin is listening to the transport
	 * properties.
	 */
	protected abstract void setLocalSocketAddress(InetSocketAddress a);

	/**
	 * Returns zero or more socket addresses for connecting to a contact with
	 * the given transport properties.
	 */
	protected abstract List<InetSocketAddress> getRemoteSocketAddresses(
			TransportProperties p);

	/**
	 * Returns true if connections to the given address can be attempted.
	 */
	protected abstract boolean isConnectable(InetSocketAddress remote);

	TcpPlugin(Executor ioExecutor, Backoff backoff,
			DuplexPluginCallback callback, int maxLatency, int maxIdleTime) {
		this.ioExecutor = ioExecutor;
		this.backoff = backoff;
		this.callback = callback;
		this.maxLatency = maxLatency;
		this.maxIdleTime = maxIdleTime;
		if (maxIdleTime > Integer.MAX_VALUE / 2)
			socketTimeout = Integer.MAX_VALUE;
		else socketTimeout = maxIdleTime * 2;
	}

	@Override
	public int getMaxLatency() {
		return maxLatency;
	}

	@Override
	public int getMaxIdleTime() {
		return maxIdleTime;
	}

	@Override
	public void start() {
		if (used.getAndSet(true)) throw new IllegalStateException();
		running = true;
		bind();
	}

	protected void bind() {
		ioExecutor.execute(() -> {
			if (!running) return;
			ServerSocket ss = null;
			for (InetSocketAddress addr : getLocalSocketAddresses()) {
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
				LOG.info("Could not bind server socket");
				return;
			}
			if (!running) {
				tryToClose(ss);
				return;
			}
			socket = ss;
			backoff.reset();
			InetSocketAddress local =
					(InetSocketAddress) ss.getLocalSocketAddress();
			setLocalSocketAddress(local);
			if (LOG.isLoggable(INFO))
				LOG.info("Listening on " + scrubSocketAddress(local));
			callback.transportEnabled();
			acceptContactConnections();
		});
	}

	protected void tryToClose(@Nullable ServerSocket ss) {
		try {
			if (ss != null) ss.close();
		} catch (IOException e) {
			if (LOG.isLoggable(WARNING)) LOG.log(WARNING, e.toString(), e);
		} finally {
			callback.transportDisabled();
		}
	}

	String getIpPortString(InetSocketAddress a) {
		String addr = a.getAddress().getHostAddress();
		int percent = addr.indexOf('%');
		if (percent != -1) addr = addr.substring(0, percent);
		return addr + ":" + a.getPort();
	}

	private void acceptContactConnections() {
		while (isRunning()) {
			Socket s;
			try {
				s = socket.accept();
				s.setSoTimeout(socketTimeout);
			} catch (IOException e) {
				// This is expected when the socket is closed
				if (LOG.isLoggable(INFO)) LOG.info(e.toString());
				return;
			}
			if (LOG.isLoggable(INFO))
				LOG.info("Connection from " +
						scrubSocketAddress(s.getRemoteSocketAddress()));
			backoff.reset();
			TcpTransportConnection conn = new TcpTransportConnection(this, s);
			callback.incomingConnectionCreated(conn);
		}
	}

	@Override
	public void stop() {
		running = false;
		tryToClose(socket);
	}

	@Override
	public boolean isRunning() {
		return running && socket != null && !socket.isClosed();
	}

	@Override
	public boolean shouldPoll() {
		return true;
	}

	@Override
	public int getPollingInterval() {
		return backoff.getPollingInterval();
	}

	@Override
	public void poll(Collection<ContactId> connected) {
		if (!isRunning()) return;
		backoff.increment();
		Map<ContactId, TransportProperties> remote =
				callback.getRemoteProperties();
		for (Entry<ContactId, TransportProperties> e : remote.entrySet()) {
			ContactId c = e.getKey();
			if (!connected.contains(c)) connectAndCallBack(c, e.getValue());
		}
	}

	private void connectAndCallBack(ContactId c, TransportProperties p) {
		ioExecutor.execute(() -> {
			if (!isRunning()) return;
			DuplexTransportConnection d = createConnection(p);
			if (d != null) {
				backoff.reset();
				callback.outgoingConnectionCreated(c, d);
			}
		});
	}

	@Override
	public DuplexTransportConnection createConnection(ContactId c) {
		if (!isRunning()) return null;
		return createConnection(callback.getRemoteProperties(c));
	}

	@Nullable
	private DuplexTransportConnection createConnection(TransportProperties p) {
		for (InetSocketAddress remote : getRemoteSocketAddresses(p)) {
			if (!isConnectable(remote)) {
				if (LOG.isLoggable(INFO)) {
					SocketAddress local = socket.getLocalSocketAddress();
					LOG.info(scrubSocketAddress(remote) +
							" is not connectable from " +
							scrubSocketAddress(local));
				}
				continue;
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
					LOG.info("Could not connect to " +
							scrubSocketAddress(remote));
			}
		}
		return null;
	}

	@Nullable
	InetSocketAddress parseSocketAddress(String ipPort) {
		if (StringUtils.isNullOrEmpty(ipPort)) return null;
		String[] split = ipPort.split(":");
		if (split.length != 2) return null;
		String addr = split[0], port = split[1];
		// Ensure getByName() won't perform a DNS lookup
		if (!DOTTED_QUAD.matcher(addr).matches()) return null;
		try {
			InetAddress a = InetAddress.getByName(addr);
			int p = Integer.parseInt(port);
			return new InetSocketAddress(a, p);
		} catch (UnknownHostException e) {
			if (LOG.isLoggable(WARNING))
				// not scrubbing to enable us to find the problem
				LOG.warning("Invalid address: " + addr);
			return null;
		} catch (NumberFormatException e) {
			if (LOG.isLoggable(WARNING))
				LOG.warning("Invalid port: " + port);
			return null;
		}
	}

	@Override
	public boolean supportsKeyAgreement() {
		return false;
	}

	@Override
	public KeyAgreementListener createKeyAgreementListener(byte[] commitment) {
		throw new UnsupportedOperationException();
	}

	@Override
	public DuplexTransportConnection createKeyAgreementConnection(
			byte[] commitment, BdfList descriptor, long timeout) {
		throw new UnsupportedOperationException();
	}

	Collection<InetAddress> getLocalIpAddresses() {
		List<NetworkInterface> ifaces;
		try {
			ifaces = Collections.list(NetworkInterface.getNetworkInterfaces());
		} catch (SocketException e) {
			if (LOG.isLoggable(WARNING)) LOG.log(WARNING, e.toString(), e);
			return Collections.emptyList();
		}
		List<InetAddress> addrs = new ArrayList<>();
		for (NetworkInterface iface : ifaces)
			addrs.addAll(Collections.list(iface.getInetAddresses()));
		return addrs;
	}
}
