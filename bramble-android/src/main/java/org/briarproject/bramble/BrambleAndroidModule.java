package org.briarproject.bramble;

import org.briarproject.bramble.network.AndroidNetworkModule;
import org.briarproject.bramble.plugin.tor.CircumventionModule;
import org.briarproject.bramble.system.AndroidSystemModule;

import dagger.Module;

@Module(includes = {
		AndroidNetworkModule.class,
		AndroidSystemModule.class,
		CircumventionModule.class
})
public class BrambleAndroidModule {

}
