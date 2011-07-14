package net.sf.briar.invitation;

import net.sf.briar.api.invitation.InvitationWorkerFactory;

import com.google.inject.AbstractModule;

public class InvitationModule extends AbstractModule {

	@Override
	protected void configure() {
		bind(InvitationWorkerFactory.class).to(
				InvitationWorkerFactoryImpl.class);
	}
}
