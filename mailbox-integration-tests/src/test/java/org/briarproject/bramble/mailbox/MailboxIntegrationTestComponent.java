package org.briarproject.bramble.mailbox;

import org.briarproject.bramble.BrambleCoreModule;
import org.briarproject.bramble.api.contact.ContactManager;
import org.briarproject.bramble.api.crypto.CryptoComponent;
import org.briarproject.bramble.api.db.DatabaseComponent;
import org.briarproject.bramble.api.identity.AuthorFactory;
import org.briarproject.bramble.api.lifecycle.LifecycleManager;
import org.briarproject.bramble.api.mailbox.MailboxManager;
import org.briarproject.bramble.api.mailbox.MailboxSettingsManager;
import org.briarproject.bramble.api.mailbox.MailboxUpdateManager;
import org.briarproject.bramble.api.properties.TransportPropertyManager;
import org.briarproject.bramble.api.system.Clock;
import org.briarproject.bramble.mailbox.MailboxIntegrationTestUtils.TestUrlConverterModule;
import org.briarproject.bramble.test.BrambleCoreIntegrationTestModule;
import org.briarproject.bramble.test.BrambleIntegrationTestComponent;
import org.briarproject.bramble.test.FakeTorPluginConfigModule;
import org.briarproject.bramble.test.TestDnsModule;
import org.briarproject.bramble.test.TestSocksModule;

import javax.inject.Singleton;

import dagger.Component;

@Singleton
@Component(modules = {
		BrambleCoreIntegrationTestModule.class,
		BrambleCoreModule.class,
		TestUrlConverterModule.class,
		FakeTorPluginConfigModule.class,
		TestSocksModule.class,
		TestDnsModule.class,
})
interface MailboxIntegrationTestComponent extends
		BrambleIntegrationTestComponent {

	DatabaseComponent getDatabaseComponent();

	MailboxManager getMailboxManager();

	MailboxUpdateManager getMailboxUpdateManager();

	MailboxSettingsManager getMailboxSettingsManager();

	LifecycleManager getLifecycleManager();

	ContactManager getContactManager();

	Clock getClock();

	TransportPropertyManager getTransportPropertyManager();

	AuthorFactory getAuthorFactory();

	CryptoComponent getCrypto();
}
