package org.briarproject.privategroup;

import org.briarproject.api.clients.ClientHelper;
import org.briarproject.api.clients.ContactGroupFactory;
import org.briarproject.api.contact.ContactManager;
import org.briarproject.api.data.MetadataEncoder;
import org.briarproject.api.identity.AuthorFactory;
import org.briarproject.api.lifecycle.LifecycleManager;
import org.briarproject.api.messaging.ConversationManager;
import org.briarproject.api.privategroup.GroupMessageFactory;
import org.briarproject.api.privategroup.PrivateGroupFactory;
import org.briarproject.api.privategroup.PrivateGroupManager;
import org.briarproject.api.privategroup.invitation.GroupInvitationManager;
import org.briarproject.api.sync.ValidationManager;
import org.briarproject.api.system.Clock;
import org.briarproject.privategroup.invitation.GroupInvitationManagerImpl;

import javax.inject.Inject;
import javax.inject.Singleton;

import dagger.Module;
import dagger.Provides;

@Module
public class PrivateGroupModule {

	public static class EagerSingletons {
		@Inject
		GroupMessageValidator groupMessageValidator;
		@Inject
		GroupInvitationManager groupInvitationManager;
	}

	@Provides
	@Singleton
	PrivateGroupManager provideForumManager(
			PrivateGroupManagerImpl groupManager,
			ValidationManager validationManager) {

		validationManager
				.registerIncomingMessageHook(PrivateGroupManager.CLIENT_ID,
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
			ContactGroupFactory contactGroupFactory,
			PrivateGroupFactory groupFactory,
			ValidationManager validationManager, ClientHelper clientHelper,
			MetadataEncoder metadataEncoder, Clock clock,
			AuthorFactory authorFactory,
			GroupInvitationManager groupInvitationManager) {

		GroupMessageValidator validator = new GroupMessageValidator(
				contactGroupFactory, groupFactory, clientHelper,
				metadataEncoder, clock, authorFactory);
		validationManager.registerMessageValidator(
				PrivateGroupManager.CLIENT_ID, validator);

		return validator;
	}

	@Provides
	@Singleton
	GroupInvitationManager provideGroupInvitationManager(
			LifecycleManager lifecycleManager, ContactManager contactManager,
			GroupInvitationManagerImpl groupInvitationManager,
			ConversationManager conversationManager,
			ValidationManager validationManager) {

		validationManager.registerIncomingMessageHook(
				GroupInvitationManager.CLIENT_ID, groupInvitationManager);
		lifecycleManager.registerClient(groupInvitationManager);
		contactManager.registerAddContactHook(groupInvitationManager);
		contactManager.registerRemoveContactHook(groupInvitationManager);
		conversationManager.registerConversationClient(groupInvitationManager);

		return groupInvitationManager;
	}

}
