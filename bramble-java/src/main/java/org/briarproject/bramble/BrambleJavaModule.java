package org.briarproject.bramble;

import org.briarproject.bramble.network.JavaNetworkModule;
import org.briarproject.bramble.plugin.tor.CircumventionModule;
import org.briarproject.bramble.socks.SocksModule;
import org.briarproject.bramble.system.JavaSystemModule;

import dagger.Module;

@Module(includes = {
		JavaNetworkModule.class,
		JavaSystemModule.class,
		CircumventionModule.class,
		SocksModule.class
})
public class BrambleJavaModule {

}
