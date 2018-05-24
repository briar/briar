package org.briarproject.bramble;

import org.briarproject.bramble.system.AndroidSystemModule;

import dagger.Module;

@Module(includes = {
		AndroidSystemModule.class
})
public class BrambleAndroidModule {
}
