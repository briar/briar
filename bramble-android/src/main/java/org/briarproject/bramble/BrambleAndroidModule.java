package org.briarproject.bramble;

import org.briarproject.bramble.network.AndroidNetworkModule;
import org.briarproject.bramble.plugin.tor.AndroidCircumventionModule;
import org.briarproject.bramble.system.AndroidSystemModule;

import dagger.Module;

@Module(includes = {
		AndroidCircumventionModule.class,
		AndroidNetworkModule.class,
		AndroidSystemModule.class
})
public class BrambleAndroidModule {

}
