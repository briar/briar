package org.briarproject.properties;

import org.briarproject.api.contact.ContactManager;
import org.briarproject.api.data.BdfReaderFactory;
import org.briarproject.api.data.MetadataEncoder;
import org.briarproject.api.properties.TransportPropertyManager;
import org.briarproject.api.sync.ValidationManager;
import org.briarproject.api.system.Clock;
import org.briarproject.contact.ContactModule;
import org.briarproject.data.DataModule;
import org.briarproject.sync.SyncModule;

import javax.inject.Singleton;

import dagger.Module;
import dagger.Provides;

import static org.briarproject.properties.TransportPropertyManagerImpl.CLIENT_ID;

@Module
public class PropertiesModule {

	@Provides
	@Singleton
	TransportPropertyValidator getValidator(ValidationManager validationManager,
			BdfReaderFactory bdfReaderFactory, MetadataEncoder metadataEncoder,
			Clock clock) {
		TransportPropertyValidator validator = new TransportPropertyValidator(
				bdfReaderFactory, metadataEncoder, clock);
		validationManager.registerMessageValidator(CLIENT_ID, validator);
		return validator;
	}

	@Provides @Singleton
	TransportPropertyManager getTransportPropertyManager(
			ContactManager contactManager,
			TransportPropertyManagerImpl transportPropertyManager) {
		contactManager.registerAddContactHook(transportPropertyManager);
		contactManager.registerRemoveContactHook(transportPropertyManager);
		return transportPropertyManager;
	}
}
