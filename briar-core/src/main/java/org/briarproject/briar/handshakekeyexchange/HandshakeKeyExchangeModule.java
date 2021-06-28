package org.briarproject.briar.handshakekeyexchange;

import org.briarproject.briar.api.handshakekeyexchange.HandshakeKeyExchangeManager;

import javax.inject.Inject;
import javax.inject.Singleton;

import dagger.Module;
import dagger.Provides;

@Module
public class HandshakeKeyExchangeModule {
	public static class EagerSingletons {
		@Inject
		HandshakeKeyExchangeManager handshakeKeyExchangeManager;
	}

	@Provides
	@Singleton
	HandshakeKeyExchangeManager handshakeKeyExchangeManager(HandshakeKeyExchangeManagerImpl handshakeKeyExchangeManager) {
		return handshakeKeyExchangeManager;
	}
}
