package org.briarproject.privategroup;

import org.briarproject.api.clients.ClientHelper;
import org.briarproject.api.crypto.CryptoComponent;
import org.briarproject.api.data.MetadataEncoder;
import org.briarproject.api.identity.AuthorFactory;
import org.briarproject.api.privategroup.GroupMessageFactory;
import org.briarproject.api.privategroup.PrivateGroupFactory;
import org.briarproject.api.privategroup.PrivateGroupManager;
import org.briarproject.api.sync.GroupFactory;
import org.briarproject.api.sync.ValidationManager;
import org.briarproject.api.system.Clock;

import java.security.SecureRandom;

import javax.inject.Inject;
import javax.inject.Singleton;

import dagger.Module;
import dagger.Provides;

@Module
public class PrivateGroupModule {

	public static class EagerSingletons {
		@Inject
		GroupMessageValidator groupMessageValidator;
	}

	@Provides
	@Singleton
	PrivateGroupManager provideForumManager(
			PrivateGroupManagerImpl groupManager,
			ValidationManager validationManager) {

		validationManager
				.registerIncomingMessageHook(groupManager.getClientId(),
						groupManager);

		return groupManager;
	}

	@Provides
	PrivateGroupFactory providePrivateGroupFactory(
			PrivateGroupFactoryImpl privateGroupFactory) {
		return privateGroupFactory;
	}

	@Provides
	GroupMessageFactory provideGroupMessageFactory(
			GroupMessageFactoryImpl groupMessageFactory) {
		return groupMessageFactory;
	}

	@Provides
	@Singleton
	GroupMessageValidator provideGroupMessageValidator(
			ValidationManager validationManager, CryptoComponent crypto,
			AuthorFactory authorFactory, ClientHelper clientHelper,
			MetadataEncoder metadataEncoder, Clock clock) {
		GroupMessageValidator validator = new GroupMessageValidator(crypto,
				authorFactory, clientHelper, metadataEncoder, clock);
		validationManager.registerMessageValidator(
				PrivateGroupManagerImpl.CLIENT_ID, validator);
		return validator;
	}

}
