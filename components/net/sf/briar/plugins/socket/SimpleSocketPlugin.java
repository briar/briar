package net.sf.briar.plugins.socket;

import java.net.InetSocketAddress;
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
		return createSocketAddress(localProperties);
	}

	@Override
	protected SocketAddress getSocketAddress(ContactId c) {
		Map<String, String> properties = remoteProperties.get(c);
		if(properties == null) return null;
		return createSocketAddress(properties);
	}

	private SocketAddress createSocketAddress(Map<String, String> properties) {
		String host = properties.get("host");
		String portString = properties.get("port");
		if(host == null || portString == null) return null;
		int port;
		try {
			port = Integer.valueOf(portString);
		} catch(NumberFormatException e) {
			return null;
		}
		return InetSocketAddress.createUnresolved(host, port);
	}

	@Override
	protected Socket createClientSocket() {
		return new Socket();
	}

	@Override
	protected Socket createServerSocket() {
		return new Socket();
	}
}
