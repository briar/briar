package briarproject;

import org.briarproject.TestDatabaseModule;
import org.briarproject.TestPluginsModule;
import org.briarproject.TestSystemModule;
import org.briarproject.api.contact.ContactManager;
import org.briarproject.api.event.EventBus;
import org.briarproject.api.identity.IdentityManager;
import org.briarproject.api.lifecycle.LifecycleManager;
import org.briarproject.api.messaging.MessagingManager;
import org.briarproject.api.messaging.PrivateMessageFactory;
import org.briarproject.api.sync.SyncSessionFactory;
import org.briarproject.api.transport.KeyManager;
import org.briarproject.api.transport.StreamReaderFactory;
import org.briarproject.api.transport.StreamWriterFactory;
import org.briarproject.clients.ClientsModule;
import org.briarproject.contact.ContactModule;
import org.briarproject.crypto.CryptoModule;
import org.briarproject.data.DataModule;
import org.briarproject.db.DatabaseModule;
import org.briarproject.event.EventModule;
import org.briarproject.identity.IdentityModule;
import org.briarproject.lifecycle.LifecycleModule;
import org.briarproject.messaging.MessagingModule;
import org.briarproject.plugins.PluginsModule;
import org.briarproject.sync.SyncModule;
import org.briarproject.transport.TransportModule;

import javax.inject.Singleton;

import dagger.Component;

@Singleton
@Component(modules = {
		TestDatabaseModule.class,
		TestPluginsModule.class,
		TestSystemModule.class,
		ClientsModule.class,
		ContactModule.class,
		CryptoModule.class,
		DataModule.class,
		DatabaseModule.class,
		EventModule.class,
		IdentityModule.class,
		LifecycleModule.class,
		MessagingModule.class,
		PluginsModule.class,
		SyncModule.class,
		TransportModule.class
})
public interface SimplexMessagingIntegrationTestComponent {

	void inject(SimplexMessagingIntegrationTest testCase);

	LifecycleManager getLifecycleManager();

	IdentityManager getIdentityManager();

	ContactManager getContactManager();

	MessagingManager getMessagingManager();

	KeyManager getKeyManager();

	PrivateMessageFactory getPrivateMessageFactory();

	EventBus getEventBus();

	StreamWriterFactory getStreamWriterFactory();

	StreamReaderFactory getStreamReaderFactory();

	SyncSessionFactory getSyncSessionFactory();
}
