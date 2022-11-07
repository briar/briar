package org.briarproject.bramble.plugin.file;

import org.briarproject.bramble.BrambleCoreEagerSingletons;
import org.briarproject.bramble.BrambleCoreModule;
import org.briarproject.bramble.api.contact.ContactManager;
import org.briarproject.bramble.api.event.EventBus;
import org.briarproject.bramble.api.identity.IdentityManager;
import org.briarproject.bramble.api.lifecycle.LifecycleManager;
import org.briarproject.bramble.api.plugin.file.RemovableDriveManager;
import org.briarproject.bramble.battery.DefaultBatteryManagerModule;
import org.briarproject.bramble.event.DefaultEventExecutorModule;
import org.briarproject.bramble.mailbox.ModularMailboxModule;
import org.briarproject.bramble.system.DefaultThreadFactoryModule;
import org.briarproject.bramble.system.DefaultWakefulIoExecutorModule;
import org.briarproject.bramble.system.TimeTravelModule;
import org.briarproject.bramble.test.TestDatabaseConfigModule;
import org.briarproject.bramble.test.TestDnsModule;
import org.briarproject.bramble.test.TestFeatureFlagModule;
import org.briarproject.bramble.test.TestMailboxDirectoryModule;
import org.briarproject.bramble.test.TestSecureRandomModule;
import org.briarproject.bramble.test.TestSocksModule;

import javax.inject.Singleton;

import dagger.Component;

@Singleton
@Component(modules = {
		BrambleCoreModule.class,
		DefaultBatteryManagerModule.class,
		DefaultEventExecutorModule.class,
		DefaultWakefulIoExecutorModule.class,
		DefaultThreadFactoryModule.class,
		TestDatabaseConfigModule.class,
		TestDnsModule.class,
		TestFeatureFlagModule.class,
		TestMailboxDirectoryModule.class,
		RemovableDriveIntegrationTestModule.class,
		RemovableDriveModule.class,
		ModularMailboxModule.class,
		TestSecureRandomModule.class,
		TimeTravelModule.class,
		TestSocksModule.class,
})
interface RemovableDriveIntegrationTestComponent
		extends BrambleCoreEagerSingletons {

	ContactManager getContactManager();

	EventBus getEventBus();

	IdentityManager getIdentityManager();

	LifecycleManager getLifecycleManager();

	RemovableDriveManager getRemovableDriveManager();

	class Helper {

		public static void injectEagerSingletons(
				RemovableDriveIntegrationTestComponent c) {
			BrambleCoreEagerSingletons.Helper.injectEagerSingletons(c);
		}
	}
}
