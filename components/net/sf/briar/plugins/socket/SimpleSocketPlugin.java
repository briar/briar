package net.sf.briar.plugins.socket;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketAddress;
import java.util.concurrent.Executor;

import net.sf.briar.api.ContactId;
import net.sf.briar.api.TransportId;
import net.sf.briar.api.TransportProperties;
import net.sf.briar.api.plugins.StreamTransportCallback;

class SimpleSocketPlugin extends SocketPlugin {

	public static final int TRANSPORT_ID = 1;

	private static final TransportId id = new TransportId(TRANSPORT_ID);

	private final long pollingInterval;

	SimpleSocketPlugin(Executor executor, StreamTransportCallback callback,
			long pollingInterval) {
		super(executor, callback);
		this.pollingInterval = pollingInterval;
	}

	public TransportId getId() {
		return id;
	}

	public boolean shouldPoll() {
		return true;
	}

	public long getPollingInterval() {
		return pollingInterval;
	}

	@Override
	protected SocketAddress getLocalSocketAddress() {
		assert started;
		return createSocketAddress(localProperties);
	}

	@Override
	protected SocketAddress getSocketAddress(ContactId c) {
		assert started;
		TransportProperties properties = remoteProperties.get(c);
		if(properties == null) return null;
		return createSocketAddress(properties);
	}

	private SocketAddress createSocketAddress(TransportProperties properties) {
		assert properties != null;
		String host = properties.get("host");
		String portString = properties.get("port");
		if(host == null || portString == null) return null;
		int port;
		try {
			port = Integer.valueOf(portString);
		} catch(NumberFormatException e) {
			return null;
		}
		return new InetSocketAddress(host, port);
	}

	@Override
	protected void setLocalSocketAddress(SocketAddress s) {
		TransportProperties p;
		synchronized(this) {
			if(!started) return;
			p = new TransportProperties(localProperties);
		}
		if(!(s instanceof InetSocketAddress))
			throw new IllegalArgumentException();
		InetSocketAddress i = (InetSocketAddress) s;
		String host = i.getAddress().getHostAddress();
		String port = String.valueOf(i.getPort());
		// FIXME: Special handling for private IP addresses?
		p.put("host", host);
		p.put("port", port);
		callback.setLocalProperties(p);
	}

	@Override
	protected Socket createClientSocket() throws IOException {
		assert started;
		return new Socket();
	}

	@Override
	protected ServerSocket createServerSocket() throws IOException {
		assert started;
		return new ServerSocket();
	}
}
