package org.briarproject.bramble.network;

import org.briarproject.bramble.api.lifecycle.LifecycleManager;
import org.briarproject.bramble.api.network.NetworkManager;

import javax.inject.Singleton;

import dagger.Module;
import dagger.Provides;

@Module
public class JavaNetworkModule {

	@Provides
	@Singleton
	NetworkManager provideNetworkManager(LifecycleManager lifecycleManager,
			JavaNetworkManager networkManager) {
		lifecycleManager.registerService(networkManager);
		return networkManager;
	}
}
