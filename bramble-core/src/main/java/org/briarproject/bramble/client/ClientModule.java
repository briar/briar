package org.briarproject.bramble.client;

import org.briarproject.bramble.api.client.ClientHelper;
import org.briarproject.bramble.api.client.ContactGroupFactory;

import dagger.Module;
import dagger.Provides;

@Module
public class ClientModule {

	@Provides
	ClientHelper provideClientHelper(ClientHelperImpl clientHelper) {
		return clientHelper;
	}

	@Provides
	ContactGroupFactory provideContactGroupFactory(
			ContactGroupFactoryImpl contactGroupFactory) {
		return contactGroupFactory;
	}

}
