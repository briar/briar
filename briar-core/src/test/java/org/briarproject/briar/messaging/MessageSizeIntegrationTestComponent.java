package org.briarproject.briar.messaging;

import org.briarproject.bramble.BrambleCoreIntegrationTestEagerSingletons;
import org.briarproject.bramble.BrambleCoreModule;
import org.briarproject.bramble.test.BrambleCoreIntegrationTestModule;
import org.briarproject.briar.autodelete.AutoDeleteModule;
import org.briarproject.briar.avatar.AvatarModule;
import org.briarproject.briar.client.BriarClientModule;
import org.briarproject.briar.conversation.ConversationModule;
import org.briarproject.briar.forum.ForumModule;
import org.briarproject.briar.identity.IdentityModule;

import javax.inject.Singleton;

import dagger.Component;

@Singleton
@Component(modules = {
		BrambleCoreIntegrationTestModule.class,
		BrambleCoreModule.class,
		BriarClientModule.class,
		AutoDeleteModule.class,
		AvatarModule.class,
		ConversationModule.class,
		ForumModule.class,
		IdentityModule.class,
		MessagingModule.class
})
interface MessageSizeIntegrationTestComponent
		extends BrambleCoreIntegrationTestEagerSingletons {

	void inject(MessageSizeIntegrationTest testCase);

	void inject(AvatarModule.EagerSingletons init);

	void inject(ForumModule.EagerSingletons init);

	void inject(MessagingModule.EagerSingletons init);

	class Helper {

		public static void injectEagerSingletons(
				MessageSizeIntegrationTestComponent c) {
			BrambleCoreIntegrationTestEagerSingletons.Helper
					.injectEagerSingletons(c);
			c.inject(new AvatarModule.EagerSingletons());
			c.inject(new ForumModule.EagerSingletons());
			c.inject(new MessagingModule.EagerSingletons());
		}
	}
}
