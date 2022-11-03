package org.briarproject.bramble.mailbox;

import org.briarproject.bramble.BrambleCoreIntegrationTestEagerSingletons;
import org.briarproject.bramble.BrambleCoreModule;
import org.briarproject.bramble.api.contact.ContactManager;
import org.briarproject.bramble.api.db.DatabaseComponent;
import org.briarproject.bramble.api.identity.AuthorFactory;
import org.briarproject.bramble.api.lifecycle.LifecycleManager;
import org.briarproject.bramble.api.mailbox.MailboxManager;
import org.briarproject.bramble.api.mailbox.MailboxSettingsManager;
import org.briarproject.bramble.api.mailbox.MailboxUpdateManager;
import org.briarproject.bramble.api.properties.TransportPropertyManager;
import org.briarproject.bramble.api.system.Clock;
import org.briarproject.bramble.test.BrambleCoreIntegrationTestModule;
import org.briarproject.bramble.test.BrambleIntegrationTestComponent;
import org.briarproject.bramble.test.MailboxTestPluginConfigModule;
import org.briarproject.bramble.test.TestDnsModule;
import org.briarproject.bramble.test.TestSocksModule;

import javax.inject.Singleton;

import dagger.Component;

@Singleton
@Component(modules = {
		BrambleCoreIntegrationTestModule.class,
		BrambleCoreModule.class,
		TestModularMailboxModule.class,
		MailboxTestPluginConfigModule.class,
		TestSocksModule.class,
		TestDnsModule.class,
})
interface MailboxIntegrationTestComponent extends
		BrambleIntegrationTestComponent {

	LifecycleManager getLifecycleManager();

	DatabaseComponent getDatabaseComponent();

	ContactManager getContactManager();

	AuthorFactory getAuthorFactory();

	Clock getClock();

	MailboxManager getMailboxManager();

	MailboxSettingsManager getMailboxSettingsManager();

	MailboxUpdateManager getMailboxUpdateManager();

	TransportPropertyManager getTransportPropertyManager();

	class Helper {
		static void injectEagerSingletons(
				MailboxIntegrationTestComponent c) {
			BrambleCoreIntegrationTestEagerSingletons.Helper
					.injectEagerSingletons(c);
		}
	}
}
