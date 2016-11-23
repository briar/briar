package org.briarproject.bramble;

import org.briarproject.bramble.plugin.AndroidPluginModule;
import org.briarproject.bramble.system.AndroidSystemModule;

import dagger.Module;

@Module(includes = {
		AndroidPluginModule.class,
		AndroidSystemModule.class
})
public class BrambleAndroidModule {
}
