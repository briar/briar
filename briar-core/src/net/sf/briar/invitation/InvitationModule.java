package net.sf.briar.invitation;

import javax.inject.Singleton;

import net.sf.briar.api.invitation.InvitationTaskFactory;

import com.google.inject.AbstractModule;

public class InvitationModule extends AbstractModule {

	protected void configure() {
		bind(InvitationTaskFactory.class).to(
				InvitationTaskFactoryImpl.class).in(Singleton.class);
	}
}
