package org.briarproject.bramble.test;

import org.briarproject.bramble.api.plugin.TorControlPort;
import org.briarproject.bramble.api.plugin.TorSocksPort;

import dagger.Module;
import dagger.Provides;

import static org.briarproject.bramble.api.plugin.TorConstants.DEFAULT_CONTROL_PORT;
import static org.briarproject.bramble.api.plugin.TorConstants.DEFAULT_SOCKS_PORT;

@Module
class TestTorPortsModule {

	@Provides
	@TorSocksPort
	int provideTorSocksPort() {
		return DEFAULT_SOCKS_PORT + 10;
	}

	@Provides
	@TorControlPort
	int provideTorControlPort() {
		return DEFAULT_CONTROL_PORT + 10;
	}
}
