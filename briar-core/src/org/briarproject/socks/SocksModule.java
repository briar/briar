package org.briarproject.socks;

import java.net.InetSocketAddress;

import javax.net.SocketFactory;

import dagger.Module;
import dagger.Provides;

import static org.briarproject.api.plugins.TorConstants.CONNECT_TO_PROXY_TIMEOUT;
import static org.briarproject.api.plugins.TorConstants.SOCKS_PORT;

@Module
public class SocksModule {

	@Provides
	SocketFactory provideTorSocketFactory() {
		InetSocketAddress proxy = new InetSocketAddress("127.0.0.1",
				SOCKS_PORT);
		return new SocksSocketFactory(proxy, CONNECT_TO_PROXY_TIMEOUT);
	}
}
