package org.briarproject.bramble.mailbox;

import org.briarproject.bramble.BrambleCoreIntegrationTestEagerSingletons;
import org.briarproject.bramble.BrambleCoreModule;
import org.briarproject.bramble.api.mailbox.MailboxManager;
import org.briarproject.bramble.api.mailbox.MailboxSettingsManager;
import org.briarproject.bramble.api.mailbox.MailboxUpdateManager;
import org.briarproject.bramble.api.plugin.PluginManager;
import org.briarproject.bramble.test.BrambleCoreIntegrationTestModule;
import org.briarproject.bramble.test.MailboxTestPluginConfigModule;
import org.briarproject.bramble.test.TestDnsModule;
import org.briarproject.bramble.test.TestSocksModule;
import org.briarproject.briar.BriarCoreModule;
import org.briarproject.briar.identity.IdentityModule;
import org.briarproject.briar.messaging.MessagingModule;
import org.briarproject.briar.test.BriarIntegrationTestComponent;

import javax.inject.Singleton;

import dagger.Component;

@Singleton
@Component(modules = {
		BrambleCoreIntegrationTestModule.class,
		BrambleCoreModule.class,
		BriarCoreModule.class,
		TestUrlConverterModule.class,
		MailboxTestPluginConfigModule.class,
		TestSocksModule.class,
		TestDnsModule.class,
})
interface MailboxIntegrationTestComponent extends
		BriarIntegrationTestComponent {

	MailboxManager getMailboxManager();

	MailboxSettingsManager getMailboxSettingsManager();

	MailboxUpdateManager getMailboxUpdateManager();

	PluginManager getPluginManager();

	class Helper {
		static void injectEagerSingletons(
				MailboxIntegrationTestComponent c) {
			BrambleCoreIntegrationTestEagerSingletons.Helper
					.injectEagerSingletons(c);
			c.inject(new IdentityModule.EagerSingletons());
			c.inject(new MessagingModule.EagerSingletons());
		}
	}
}
