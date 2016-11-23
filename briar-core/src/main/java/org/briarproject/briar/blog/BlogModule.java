package org.briarproject.briar.blog;

import org.briarproject.bramble.api.client.ClientHelper;
import org.briarproject.bramble.api.contact.ContactManager;
import org.briarproject.bramble.api.data.MetadataEncoder;
import org.briarproject.bramble.api.identity.AuthorFactory;
import org.briarproject.bramble.api.lifecycle.LifecycleManager;
import org.briarproject.bramble.api.sync.GroupFactory;
import org.briarproject.bramble.api.sync.MessageFactory;
import org.briarproject.bramble.api.sync.ValidationManager;
import org.briarproject.bramble.api.system.Clock;
import org.briarproject.briar.api.blog.BlogFactory;
import org.briarproject.briar.api.blog.BlogManager;
import org.briarproject.briar.api.blog.BlogPostFactory;

import javax.inject.Inject;
import javax.inject.Singleton;

import dagger.Module;
import dagger.Provides;

import static org.briarproject.briar.blog.BlogManagerImpl.CLIENT_ID;

@Module
public class BlogModule {

	public static class EagerSingletons {
		@Inject
		BlogPostValidator blogPostValidator;
		@Inject
		BlogManager blogManager;
	}

	@Provides
	@Singleton
	BlogManager provideBlogManager(BlogManagerImpl blogManager,
			LifecycleManager lifecycleManager, ContactManager contactManager,
			ValidationManager validationManager) {

		lifecycleManager.registerClient(blogManager);
		contactManager.registerAddContactHook(blogManager);
		contactManager.registerRemoveContactHook(blogManager);
		validationManager.registerIncomingMessageHook(CLIENT_ID, blogManager);
		return blogManager;
	}

	@Provides
	BlogPostFactory provideBlogPostFactory(ClientHelper clientHelper,
			Clock clock) {
		return new BlogPostFactoryImpl(clientHelper, clock);
	}

	@Provides
	BlogFactory provideBlogFactory(GroupFactory groupFactory,
			AuthorFactory authorFactory, ClientHelper clientHelper) {
		return new BlogFactoryImpl(groupFactory, authorFactory, clientHelper);
	}

	@Provides
	@Singleton
	BlogPostValidator provideBlogPostValidator(
			ValidationManager validationManager, GroupFactory groupFactory,
			MessageFactory messageFactory, BlogFactory blogFactory,
			ClientHelper clientHelper, MetadataEncoder metadataEncoder,
			Clock clock) {

		BlogPostValidator validator = new BlogPostValidator(groupFactory,
				messageFactory, blogFactory, clientHelper, metadataEncoder,
				clock);
		validationManager.registerMessageValidator(CLIENT_ID, validator);

		return validator;
	}

}
