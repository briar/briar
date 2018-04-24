package org.briarproject.briar.messaging;

import org.briarproject.bramble.client.ClientModule;
import org.briarproject.bramble.contact.ContactModule;
import org.briarproject.bramble.crypto.CryptoExecutorModule;
import org.briarproject.bramble.crypto.CryptoModule;
import org.briarproject.bramble.data.DataModule;
import org.briarproject.bramble.db.DatabaseModule;
import org.briarproject.bramble.event.EventModule;
import org.briarproject.bramble.identity.IdentityModule;
import org.briarproject.bramble.sync.SyncModule;
import org.briarproject.bramble.system.SystemModule;
import org.briarproject.bramble.test.TestDatabaseModule;
import org.briarproject.bramble.test.TestLifecycleModule;
import org.briarproject.bramble.test.TestPluginConfigModule;
import org.briarproject.bramble.test.TestSecureRandomModule;
import org.briarproject.bramble.transport.TransportModule;
import org.briarproject.bramble.versioning.VersioningModule;
import org.briarproject.briar.client.BriarClientModule;
import org.briarproject.briar.forum.ForumModule;

import javax.inject.Singleton;

import dagger.Component;

@Singleton
@Component(modules = {
		TestDatabaseModule.class,
		TestLifecycleModule.class,
		TestPluginConfigModule.class,
		TestSecureRandomModule.class,
		BriarClientModule.class,
		ClientModule.class,
		ContactModule.class,
		CryptoModule.class,
		CryptoExecutorModule.class,
		DataModule.class,
		DatabaseModule.class,
		EventModule.class,
		ForumModule.class,
		IdentityModule.class,
		MessagingModule.class,
		SyncModule.class,
		SystemModule.class,
		TransportModule.class,
		VersioningModule.class
})
interface MessageSizeIntegrationTestComponent {

	void inject(MessageSizeIntegrationTest testCase);

	void inject(ContactModule.EagerSingletons init);

	void inject(CryptoExecutorModule.EagerSingletons init);

	void inject(ForumModule.EagerSingletons init);

	void inject(IdentityModule.EagerSingletons init);

	void inject(MessagingModule.EagerSingletons init);

	void inject(SyncModule.EagerSingletons init);

	void inject(SystemModule.EagerSingletons init);

	void inject(TransportModule.EagerSingletons init);

	void inject(VersioningModule.EagerSingletons init);
}
