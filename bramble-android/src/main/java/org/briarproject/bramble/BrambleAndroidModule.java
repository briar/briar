package org.briarproject.bramble;

import org.briarproject.bramble.plugin.tor.BridgeProvider;
import org.briarproject.bramble.plugin.tor.BridgeProviderImpl;
import org.briarproject.bramble.system.AndroidSystemModule;

import javax.inject.Singleton;

import dagger.Module;
import dagger.Provides;

@Module(includes = {
		AndroidSystemModule.class
})
public class BrambleAndroidModule {

	@Provides
	@Singleton
	BridgeProvider provideBridgeProvider() {
		return new BridgeProviderImpl();
	}

}
