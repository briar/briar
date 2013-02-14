package net.sf.briar.invitation;

import net.sf.briar.api.invitation.InvitationTaskFactory;

import com.google.inject.AbstractModule;
import com.google.inject.Singleton;

public class InvitationModule extends AbstractModule {

	@Override
	protected void configure() {
		bind(InvitationTaskFactory.class).to(InvitationTaskFactoryImpl.class).in(
				Singleton.class);
	}
}
