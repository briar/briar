package org.briarproject;

import org.briarproject.bramble.client.ClientModule;
import org.briarproject.bramble.crypto.CryptoModule;
import org.briarproject.bramble.data.DataModule;
import org.briarproject.bramble.db.DatabaseModule;
import org.briarproject.bramble.event.EventModule;
import org.briarproject.bramble.identity.IdentityModule;
import org.briarproject.bramble.sync.SyncModule;
import org.briarproject.bramble.system.SystemModule;
import org.briarproject.briar.client.BriarClientModule;
import org.briarproject.briar.forum.ForumModule;
import org.briarproject.briar.messaging.MessagingModule;

import javax.inject.Singleton;

import dagger.Component;

@Singleton
@Component(modules = {
		TestDatabaseModule.class,
		TestLifecycleModule.class,
		TestSeedProviderModule.class,
		BriarClientModule.class,
		ClientModule.class,
		CryptoModule.class,
		DataModule.class,
		DatabaseModule.class,
		EventModule.class,
		ForumModule.class,
		IdentityModule.class,
		MessagingModule.class,
		SyncModule.class,
		SystemModule.class
})
interface MessageSizeIntegrationTestComponent {

	void inject(MessageSizeIntegrationTest testCase);

	void inject(SystemModule.EagerSingletons init);
}
