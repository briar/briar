package org.briarproject.sync;

import org.briarproject.TestDatabaseModule;
import org.briarproject.TestPluginsModule;
import org.briarproject.TestSystemModule;
import org.briarproject.api.contact.ContactManager;
import org.briarproject.api.db.DatabaseComponent;
import org.briarproject.api.event.EventBus;
import org.briarproject.api.identity.IdentityManager;
import org.briarproject.api.lifecycle.LifecycleManager;
import org.briarproject.api.messaging.MessagingManager;
import org.briarproject.api.messaging.PrivateMessageFactory;
import org.briarproject.api.sync.PacketReaderFactory;
import org.briarproject.api.sync.PacketWriterFactory;
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
import org.briarproject.transport.TransportModule;

import javax.inject.Singleton;

import dagger.Component;

@Singleton
@Component(modules = {TestDatabaseModule.class, TestPluginsModule.class,
		TestSystemModule.class, LifecycleModule.class, ContactModule.class,
		CryptoModule.class, DatabaseModule.class, EventModule.class,
		SyncModule.class, DataModule.class, TransportModule.class,
		IdentityModule.class, MessagingModule.class, ClientsModule.class,
		PluginsModule.class})
public interface SimplexMessagingComponent {

	void inject(SimplexMessagingIntegrationTest testCase);

	LifecycleManager getLifecycleManager();

	DatabaseComponent getDatabaseComponent();

	IdentityManager getIdentityManager();

	ContactManager getContactManager();

	MessagingManager getMessagingManager();

	KeyManager getKeyManager();

	PrivateMessageFactory getPrivateMessageFactory();

	PacketWriterFactory getPacketWriterFactory();

	EventBus getEventBus();

	StreamWriterFactory getStreamWriterFactory();

	StreamReaderFactory getStreamReaderFactory();

	PacketReaderFactory getPacketReaderFactory();
}
