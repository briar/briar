package org.briarproject.bramble.test;

import org.briarproject.bramble.BrambleJavaModule;
import org.briarproject.bramble.battery.DefaultBatteryManagerModule;
import org.briarproject.bramble.client.ClientModule;
import org.briarproject.bramble.contact.ContactModule;
import org.briarproject.bramble.crypto.CryptoExecutorModule;
import org.briarproject.bramble.crypto.CryptoModule;
import org.briarproject.bramble.data.DataModule;
import org.briarproject.bramble.db.DatabaseExecutorModule;
import org.briarproject.bramble.db.DatabaseModule;
import org.briarproject.bramble.event.DefaultEventExecutorModule;
import org.briarproject.bramble.event.EventModule;
import org.briarproject.bramble.identity.IdentityModule;
import org.briarproject.bramble.lifecycle.LifecycleModule;
import org.briarproject.bramble.plugin.PluginModule;
import org.briarproject.bramble.plugin.tor.BridgeTest;
import org.briarproject.bramble.plugin.tor.CircumventionProvider;
import org.briarproject.bramble.properties.PropertiesModule;
import org.briarproject.bramble.record.RecordModule;
import org.briarproject.bramble.settings.SettingsModule;
import org.briarproject.bramble.sync.SyncModule;
import org.briarproject.bramble.sync.validation.ValidationModule;
import org.briarproject.bramble.system.SystemModule;
import org.briarproject.bramble.transport.TransportModule;
import org.briarproject.bramble.versioning.VersioningModule;

import javax.inject.Singleton;

import dagger.Component;

@Singleton
@Component(modules = {
		TestDatabaseConfigModule.class,
		TestPluginConfigModule.class,
		TestSecureRandomModule.class,
		BrambleJavaModule.class,
		ClientModule.class,
		ContactModule.class,
		CryptoExecutorModule.class,
		CryptoModule.class,
		DataModule.class,
		DatabaseExecutorModule.class,
		DatabaseModule.class,
		DefaultBatteryManagerModule.class,
		DefaultEventExecutorModule.class,
		EventModule.class,
		IdentityModule.class,
		LifecycleModule.class,
		RecordModule.class,
		PluginModule.class,
		PropertiesModule.class,
		TransportModule.class,
		SettingsModule.class,
		SyncModule.class,
		SystemModule.class,
		ValidationModule.class,
		VersioningModule.class
})
public interface BrambleJavaIntegrationTestComponent {

	void inject(BridgeTest init);

	void inject(ContactModule.EagerSingletons init);

	void inject(CryptoExecutorModule.EagerSingletons init);

	void inject(DatabaseExecutorModule.EagerSingletons init);

	void inject(IdentityModule.EagerSingletons init);

	void inject(LifecycleModule.EagerSingletons init);

	void inject(PluginModule.EagerSingletons init);

	void inject(PropertiesModule.EagerSingletons init);

	void inject(SystemModule.EagerSingletons init);

	void inject(TransportModule.EagerSingletons init);

	void inject(ValidationModule.EagerSingletons init);

	void inject(VersioningModule.EagerSingletons init);

	CircumventionProvider getCircumventionProvider();
}
