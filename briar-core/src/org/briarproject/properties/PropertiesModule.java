package org.briarproject.properties;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;

import org.briarproject.api.clients.ClientHelper;
import org.briarproject.api.contact.ContactManager;
import org.briarproject.api.data.MetadataEncoder;
import org.briarproject.api.properties.TransportPropertyManager;
import org.briarproject.api.sync.ValidationManager;
import org.briarproject.api.system.Clock;

import javax.inject.Singleton;

import static org.briarproject.properties.TransportPropertyManagerImpl.CLIENT_ID;

public class PropertiesModule extends AbstractModule {

	@Override
	protected void configure() {}

	@Provides @Singleton
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
			ContactManager contactManager,
			TransportPropertyManagerImpl transportPropertyManager) {
		contactManager.registerAddContactHook(transportPropertyManager);
		contactManager.registerRemoveContactHook(transportPropertyManager);
		return transportPropertyManager;
	}
}
