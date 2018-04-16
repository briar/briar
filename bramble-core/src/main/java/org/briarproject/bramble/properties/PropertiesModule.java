package org.briarproject.bramble.properties;

import org.briarproject.bramble.api.client.ClientHelper;
import org.briarproject.bramble.api.contact.ContactManager;
import org.briarproject.bramble.api.data.MetadataEncoder;
import org.briarproject.bramble.api.lifecycle.LifecycleManager;
import org.briarproject.bramble.api.properties.TransportPropertyManager;
import org.briarproject.bramble.api.sync.ClientVersioningManager;
import org.briarproject.bramble.api.sync.ValidationManager;
import org.briarproject.bramble.api.system.Clock;

import javax.inject.Inject;
import javax.inject.Singleton;

import dagger.Module;
import dagger.Provides;

import static org.briarproject.bramble.api.properties.TransportPropertyManager.CLIENT_ID;
import static org.briarproject.bramble.api.properties.TransportPropertyManager.MAJOR_VERSION;

@Module
public class PropertiesModule {

	public static class EagerSingletons {
		@Inject
		TransportPropertyValidator transportPropertyValidator;
		@Inject
		TransportPropertyManager transportPropertyManager;
	}

	@Provides
	@Singleton
	TransportPropertyValidator getValidator(ValidationManager validationManager,
			ClientHelper clientHelper, MetadataEncoder metadataEncoder,
			Clock clock) {
		TransportPropertyValidator validator = new TransportPropertyValidator(
				clientHelper, metadataEncoder, clock);
		validationManager.registerMessageValidator(CLIENT_ID, MAJOR_VERSION,
				validator);
		return validator;
	}

	@Provides
	@Singleton
	TransportPropertyManager getTransportPropertyManager(
			LifecycleManager lifecycleManager,
			ValidationManager validationManager, ContactManager contactManager,
			ClientVersioningManager clientVersioningManager,
			TransportPropertyManagerImpl transportPropertyManager) {
		lifecycleManager.registerClient(transportPropertyManager);
		validationManager.registerIncomingMessageHook(CLIENT_ID, MAJOR_VERSION,
				transportPropertyManager);
		contactManager.registerContactHook(transportPropertyManager);
		clientVersioningManager.registerClient(CLIENT_ID, MAJOR_VERSION);
		clientVersioningManager.registerClientVersioningHook(CLIENT_ID,
				MAJOR_VERSION, transportPropertyManager);
		return transportPropertyManager;
	}
}
