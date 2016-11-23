package org.briarproject.bramble.invitation;

import org.briarproject.bramble.api.invitation.InvitationTaskFactory;

import dagger.Module;
import dagger.Provides;

@Module
public class InvitationModule {

	@Provides
	InvitationTaskFactory provideInvitationTaskFactory(
			InvitationTaskFactoryImpl invitationTaskFactory) {
		return invitationTaskFactory;
	}
}
