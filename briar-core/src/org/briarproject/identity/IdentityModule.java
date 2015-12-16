package org.briarproject.identity;

import com.google.inject.AbstractModule;

import org.briarproject.api.identity.IdentityManager;

public class IdentityModule extends AbstractModule {

	@Override
	protected void configure() {
		bind(IdentityManager.class).to(IdentityManagerImpl.class);
	}
}
