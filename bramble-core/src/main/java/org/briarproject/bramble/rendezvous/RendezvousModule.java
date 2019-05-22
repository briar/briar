package org.briarproject.bramble.rendezvous;

import org.briarproject.bramble.api.rendezvous.RendezvousCrypto;

import dagger.Module;
import dagger.Provides;

@Module
public class RendezvousModule {

	@Provides
	RendezvousCrypto provideRendezvousCrypto(
			RendezvousCryptoImpl rendezvousCrypto) {
		return rendezvousCrypto;
	}
}
