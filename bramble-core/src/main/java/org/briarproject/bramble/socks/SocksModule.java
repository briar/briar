package org.briarproject.bramble.socks;

import org.briarproject.bramble.plugin.TorPorts;

import java.net.InetSocketAddress;

import javax.net.SocketFactory;

import dagger.Module;
import dagger.Provides;

import static org.briarproject.bramble.api.plugin.TorConstants.CONNECT_TO_PROXY_TIMEOUT;
import static org.briarproject.bramble.api.plugin.TorConstants.EXTRA_SOCKET_TIMEOUT;

@Module
public class SocksModule {

	@Provides
	SocketFactory provideTorSocketFactory(TorPorts torPorts) {
		InetSocketAddress proxy = new InetSocketAddress("127.0.0.1",
				torPorts.getSocksPort());
		return new SocksSocketFactory(proxy, CONNECT_TO_PROXY_TIMEOUT,
				EXTRA_SOCKET_TIMEOUT);
	}
}
