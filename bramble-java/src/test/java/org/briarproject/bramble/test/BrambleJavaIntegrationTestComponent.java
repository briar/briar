package org.briarproject.bramble.test;

import org.briarproject.bramble.BrambleJavaModule;
import org.briarproject.bramble.event.EventModule;
import org.briarproject.bramble.plugin.PluginModule;
import org.briarproject.bramble.plugin.tor.BridgeTest;
import org.briarproject.bramble.plugin.tor.CircumventionProvider;
import org.briarproject.bramble.system.SystemModule;

import javax.inject.Singleton;

import dagger.Component;

@Singleton
@Component(modules = {
		BrambleJavaModule.class,
		TestLifecycleModule.class,
		PluginModule.class,  // needed for BackoffFactory
		EventModule.class,
		SystemModule.class,
})
public interface BrambleJavaIntegrationTestComponent {

	void inject(BridgeTest init);

	CircumventionProvider getCircumventionProvider();
}
