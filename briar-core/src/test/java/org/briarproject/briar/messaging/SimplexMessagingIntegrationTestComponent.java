package org.briarproject.briar.messaging;

import org.briarproject.bramble.BrambleCoreEagerSingletons;
import org.briarproject.bramble.BrambleCoreModule;
import org.briarproject.bramble.api.contact.ContactManager;
import org.briarproject.bramble.api.event.EventBus;
import org.briarproject.bramble.api.identity.IdentityManager;
import org.briarproject.bramble.api.lifecycle.LifecycleManager;
import org.briarproject.bramble.api.sync.SyncSessionFactory;
import org.briarproject.bramble.api.transport.KeyManager;
import org.briarproject.bramble.api.transport.StreamReaderFactory;
import org.briarproject.bramble.api.transport.StreamWriterFactory;
import org.briarproject.bramble.test.BrambleCoreIntegrationTestModule;
import org.briarproject.briar.api.messaging.MessagingManager;
import org.briarproject.briar.api.messaging.PrivateMessageFactory;
import org.briarproject.briar.client.BriarClientModule;

import javax.inject.Singleton;

import dagger.Component;

@Singleton
@Component(modules = {
		BrambleCoreIntegrationTestModule.class,
		BrambleCoreModule.class,
		BriarClientModule.class,
		MessagingModule.class
})
interface SimplexMessagingIntegrationTestComponent
		extends BrambleCoreEagerSingletons {

	void inject(MessagingModule.EagerSingletons init);

	default void injectSimplexMessagingEagerSingletons() {
		injectBrambleCoreEagerSingletons();
		inject(new MessagingModule.EagerSingletons());
	}

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
