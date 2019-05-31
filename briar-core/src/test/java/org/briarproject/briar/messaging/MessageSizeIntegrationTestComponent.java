package org.briarproject.briar.messaging;

import org.briarproject.bramble.BrambleCoreEagerSingletons;
import org.briarproject.bramble.BrambleCoreModule;
import org.briarproject.bramble.test.BrambleCoreIntegrationTestModule;
import org.briarproject.briar.client.BriarClientModule;
import org.briarproject.briar.forum.ForumModule;

import javax.inject.Singleton;

import dagger.Component;

@Singleton
@Component(modules = {
		BrambleCoreIntegrationTestModule.class,
		BrambleCoreModule.class,
		BriarClientModule.class,
		ForumModule.class,
		MessagingModule.class
})
interface MessageSizeIntegrationTestComponent
		extends BrambleCoreEagerSingletons {

	void inject(MessageSizeIntegrationTest testCase);

	void inject(ForumModule.EagerSingletons init);

	void inject(MessagingModule.EagerSingletons init);
}
