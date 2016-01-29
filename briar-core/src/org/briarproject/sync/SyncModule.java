package org.briarproject.sync;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;

import org.briarproject.api.data.ObjectReader;
import org.briarproject.api.event.EventBus;
import org.briarproject.api.identity.Author;
import org.briarproject.api.identity.AuthorFactory;
import org.briarproject.api.lifecycle.LifecycleManager;
import org.briarproject.api.sync.Group;
import org.briarproject.api.sync.GroupFactory;
import org.briarproject.api.sync.MessageFactory;
import org.briarproject.api.sync.PacketReaderFactory;
import org.briarproject.api.sync.PacketWriterFactory;
import org.briarproject.api.sync.PrivateGroupFactory;
import org.briarproject.api.sync.SubscriptionUpdate;
import org.briarproject.api.sync.SyncSessionFactory;
import org.briarproject.api.sync.ValidationManager;

import javax.inject.Singleton;

public class SyncModule extends AbstractModule {

	@Override
	protected void configure() {
		bind(AuthorFactory.class).to(AuthorFactoryImpl.class);
		bind(GroupFactory.class).to(GroupFactoryImpl.class);
		bind(MessageFactory.class).to(MessageFactoryImpl.class);
		bind(PacketReaderFactory.class).to(PacketReaderFactoryImpl.class);
		bind(PacketWriterFactory.class).to(PacketWriterFactoryImpl.class);
		bind(PrivateGroupFactory.class).to(PrivateGroupFactoryImpl.class);
		bind(SyncSessionFactory.class).to(
				SyncSessionFactoryImpl.class).in(Singleton.class);
	}

	@Provides
	ObjectReader<Author> getAuthorReader(AuthorFactory authorFactory) {
		return new AuthorReader(authorFactory);
	}

	@Provides
	ObjectReader<Group> getGroupReader(GroupFactory groupFactory) {
		return new GroupReader(groupFactory);
	}

	@Provides
	ObjectReader<SubscriptionUpdate> getSubscriptionUpdateReader(
			ObjectReader<Group> groupReader) {
		return new SubscriptionUpdateReader(groupReader);
	}

	@Provides @Singleton
	ValidationManager getValidationManager(LifecycleManager lifecycleManager,
			EventBus eventBus, ValidationManagerImpl validationManager) {
		lifecycleManager.register(validationManager);
		eventBus.addListener(validationManager);
		return validationManager;
	}
}
