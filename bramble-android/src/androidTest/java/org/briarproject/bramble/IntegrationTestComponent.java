package org.briarproject.bramble;

import org.briarproject.bramble.event.EventModule;
import org.briarproject.bramble.plugin.PluginModule;
import org.briarproject.bramble.plugin.tor.BridgeTest;
import org.briarproject.bramble.system.SystemModule;

import javax.inject.Singleton;

import dagger.Component;

@Singleton
@Component(modules = {
		BrambleAndroidModule.class,
		PluginModule.class,  // needed for BackoffFactory
		EventModule.class,
		SystemModule.class,
})
public interface IntegrationTestComponent {

	void inject(BridgeTest init);

}
