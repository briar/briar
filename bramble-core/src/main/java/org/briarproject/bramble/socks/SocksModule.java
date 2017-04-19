package org.briarproject.bramble.socks;

import java.net.InetSocketAddress;

import javax.net.SocketFactory;

import dagger.Module;
import dagger.Provides;

import static org.briarproject.bramble.api.plugin.TorConstants.CONNECT_TO_PROXY_TIMEOUT;
import static org.briarproject.bramble.api.plugin.TorConstants.EXTRA_SOCKET_TIMEOUT;
import static org.briarproject.bramble.api.plugin.TorConstants.SOCKS_PORT;

@Module
public class SocksModule {

	@Provides
	SocketFactory provideTorSocketFactory() {
		InetSocketAddress proxy = new InetSocketAddress("127.0.0.1",
				SOCKS_PORT);
		return new SocksSocketFactory(proxy, CONNECT_TO_PROXY_TIMEOUT,
				EXTRA_SOCKET_TIMEOUT);
	}
}
