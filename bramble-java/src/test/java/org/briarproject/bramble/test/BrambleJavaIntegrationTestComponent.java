package org.briarproject.bramble.test;

import org.briarproject.bramble.BrambleCoreIntegrationTestEagerSingletons;
import org.briarproject.bramble.BrambleCoreModule;
import org.briarproject.bramble.BrambleJavaModule;
import org.briarproject.bramble.plugin.tor.BridgeTest;
import org.briarproject.bramble.plugin.tor.CircumventionProvider;

import javax.inject.Singleton;

import dagger.Component;

@Singleton
@Component(modules = {
		BrambleCoreIntegrationTestModule.class,
		BrambleCoreModule.class,
		BrambleJavaModule.class
})
public interface BrambleJavaIntegrationTestComponent
		extends BrambleCoreIntegrationTestEagerSingletons {

	void inject(BridgeTest init);

	CircumventionProvider getCircumventionProvider();
}
