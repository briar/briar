package net.sf.briar.android.invitation;

import javax.inject.Singleton;

import com.google.inject.AbstractModule;

public class AndroidInvitationModule extends AbstractModule {

	@Override
	protected void configure() {
		bind(InvitationManager.class).to(InvitationManagerImpl.class).in(
				Singleton.class);
	}
}
