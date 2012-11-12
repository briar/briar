package net.sf.briar.invitation;

import net.sf.briar.api.invitation.InvitationManager;

import com.google.inject.AbstractModule;

public class InvitationModule extends AbstractModule {

	@Override
	protected void configure() {
		bind(InvitationManager.class).to(InvitationManagerImpl.class);
	}
}
