package org.briarproject.forum;

import com.google.inject.AbstractModule;

import org.briarproject.api.forum.ForumFactory;
import org.briarproject.api.forum.ForumManager;
import org.briarproject.api.forum.ForumPostFactory;

public class ForumModule extends AbstractModule {

	@Override
	protected void configure() {
		bind(ForumFactory.class).to(ForumFactoryImpl.class);
		bind(ForumManager.class).to(ForumManagerImpl.class);
		bind(ForumPostFactory.class).to(ForumPostFactoryImpl.class);
	}
}
