package org.briarproject.properties;

import org.briarproject.api.clients.ClientHelper;
import org.briarproject.api.contact.ContactManager;
import org.briarproject.api.data.MetadataEncoder;
import org.briarproject.api.lifecycle.LifecycleManager;
import org.briarproject.api.properties.TransportPropertyManager;
import org.briarproject.api.sync.ValidationManager;
import org.briarproject.api.system.Clock;

import javax.inject.Inject;
import javax.inject.Singleton;

import dagger.Module;
import dagger.Provides;

import static org.briarproject.properties.TransportPropertyManagerImpl.CLIENT_ID;

@Module
public class PropertiesModule {

	public static class EagerSingletons {
		@Inject TransportPropertyValidator transportPropertyValidator;
		@Inject TransportPropertyManager transportPropertyManager;
	}

	@Provides
	@Singleton
	TransportPropertyValidator getValidator(ValidationManager validationManager,
			ClientHelper clientHelper, MetadataEncoder metadataEncoder,
			Clock clock) {
		TransportPropertyValidator validator = new TransportPropertyValidator(
				clientHelper, metadataEncoder, clock);
		validationManager.registerMessageValidator(CLIENT_ID, validator);
		return validator;
	}

	@Provides @Singleton
	TransportPropertyManager getTransportPropertyManager(
			LifecycleManager lifecycleManager,
			ContactManager contactManager,
			TransportPropertyManagerImpl transportPropertyManager) {
		lifecycleManager.registerClient(transportPropertyManager);
		contactManager.registerAddContactHook(transportPropertyManager);
		contactManager.registerRemoveContactHook(transportPropertyManager);
		return transportPropertyManager;
	}
}
