package briarproject;

import org.briarproject.TestDatabaseModule;
import org.briarproject.TestLifecycleModule;
import org.briarproject.TestSystemModule;
import org.briarproject.clients.ClientsModule;
import org.briarproject.crypto.CryptoModule;
import org.briarproject.data.DataModule;
import org.briarproject.db.DatabaseModule;
import org.briarproject.event.EventModule;
import org.briarproject.forum.ForumModule;
import org.briarproject.identity.IdentityModule;
import org.briarproject.messaging.MessagingModule;
import org.briarproject.sync.SyncModule;

import javax.inject.Singleton;

import dagger.Component;

@Singleton
@Component(modules = {
		TestDatabaseModule.class,
		TestLifecycleModule.class,
		TestSystemModule.class,
		ClientsModule.class,
		CryptoModule.class,
		DataModule.class,
		DatabaseModule.class,
		EventModule.class,
		ForumModule.class,
		IdentityModule.class,
		MessagingModule.class,
		SyncModule.class
})
public interface MessageSizeIntegrationTestComponent {
	void inject(MessageSizeIntegrationTest testCase);
}
