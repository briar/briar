package org.briarproject.forum;

import com.google.inject.AbstractModule;

import org.briarproject.api.forum.ForumManager;

public class ForumModule extends AbstractModule {

	@Override
	protected void configure() {
		bind(ForumManager.class).to(ForumManagerImpl.class);
	}
}
