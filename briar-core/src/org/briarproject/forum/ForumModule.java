package org.briarproject.forum;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;

import org.briarproject.api.crypto.CryptoComponent;
import org.briarproject.api.data.BdfReaderFactory;
import org.briarproject.api.data.BdfWriterFactory;
import org.briarproject.api.data.MetadataEncoder;
import org.briarproject.api.data.ObjectReader;
import org.briarproject.api.forum.ForumManager;
import org.briarproject.api.forum.ForumPostFactory;
import org.briarproject.api.identity.Author;
import org.briarproject.api.lifecycle.LifecycleManager;
import org.briarproject.api.sync.ValidationManager;
import org.briarproject.api.system.Clock;

import javax.inject.Singleton;

public class ForumModule extends AbstractModule {

	@Override
	protected void configure() {
		bind(ForumManager.class).to(ForumManagerImpl.class);
		bind(ForumPostFactory.class).to(ForumPostFactoryImpl.class);
	}

	@Provides @Singleton
	ForumPostValidator getValidator(LifecycleManager lifecycleManager,
			CryptoComponent crypto, ValidationManager validationManager,
			BdfReaderFactory bdfReaderFactory,
			BdfWriterFactory bdfWriterFactory,
			ObjectReader<Author> authorReader, MetadataEncoder metadataEncoder,
			Clock clock) {
		ForumPostValidator validator = new ForumPostValidator(crypto,
				validationManager, bdfReaderFactory, bdfWriterFactory,
				authorReader, metadataEncoder, clock);
		lifecycleManager.register(validator);
		return validator;
	}
}
