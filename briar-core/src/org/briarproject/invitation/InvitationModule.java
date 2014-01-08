package org.briarproject.invitation;

import javax.inject.Singleton;

import org.briarproject.api.invitation.InvitationTaskFactory;

import com.google.inject.AbstractModule;

public class InvitationModule extends AbstractModule {

	protected void configure() {
		bind(InvitationTaskFactory.class).to(
				InvitationTaskFactoryImpl.class).in(Singleton.class);
	}
}
