package net.sf.briar.plugins.socket;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketAddress;
import java.util.Map;
import java.util.concurrent.Executor;

import net.sf.briar.api.ContactId;
import net.sf.briar.api.TransportId;

public class SimpleSocketPlugin extends SocketPlugin {

	public static final int TRANSPORT_ID = 1;

	private static final TransportId id = new TransportId(TRANSPORT_ID);

	private final long pollingInterval;

	SimpleSocketPlugin(Executor executor, long pollingInterval) {
		super(executor);
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
		assert localProperties != null;
		return createSocketAddress(localProperties);
	}

	@Override
	protected SocketAddress getSocketAddress(ContactId c) {
		assert remoteProperties != null;
		Map<String, String> properties = remoteProperties.get(c);
		if(properties == null) return null;
		return createSocketAddress(properties);
	}

	@Override
	protected void setLocalSocketAddress(SocketAddress s) {
		assert localProperties != null;
		if(!(s instanceof InetSocketAddress))
			throw new IllegalArgumentException();
		InetSocketAddress i = (InetSocketAddress) s;
		String host = i.getAddress().getHostAddress();
		String port = String.valueOf(i.getPort());
		// FIXME: Special handling for private IP addresses?
		localProperties.put("host", host);
		localProperties.put("port", port);
		callback.setLocalProperties(localProperties);
	}

	private SocketAddress createSocketAddress(Map<String, String> properties) {
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
	protected Socket createClientSocket() throws IOException {
		return new Socket();
	}

	@Override
	protected ServerSocket createServerSocket() throws IOException {
		return new ServerSocket();
	}
}
