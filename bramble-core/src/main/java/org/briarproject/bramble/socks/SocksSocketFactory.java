package org.briarproject.bramble.socks;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketAddress;

import javax.net.SocketFactory;

class SocksSocketFactory extends SocketFactory {

	private final SocketAddress proxy;
	private final int connectToProxyTimeout, extraSocketTimeout;

	SocksSocketFactory(SocketAddress proxy, int connectToProxyTimeout,
			int extraSocketTimeout) {
		this.proxy = proxy;
		this.connectToProxyTimeout = connectToProxyTimeout;
		this.extraSocketTimeout = extraSocketTimeout;
	}

	@Override
	public Socket createSocket() {
		return new SocksSocket(proxy, connectToProxyTimeout, extraSocketTimeout);
	}

	@Override
	public Socket createSocket(String host, int port) throws IOException {
		Socket socket = createSocket();
		socket.connect(InetSocketAddress.createUnresolved(host, port));
		return socket;
	}

	@Override
	public Socket createSocket(InetAddress host, int port) {
		throw new UnsupportedOperationException();
	}

	@Override
	public Socket createSocket(String host, int port, InetAddress localHost,
			int localPort) {
		throw new UnsupportedOperationException();
	}

	@Override
	public Socket createSocket(InetAddress address, int port,
			InetAddress localAddress, int localPort) throws IOException {
		throw new UnsupportedOperationException();
	}
}
