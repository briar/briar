package org.briarproject.briar;

import org.briarproject.briar.blog.BlogModule;
import org.briarproject.briar.client.BriarClientModule;
import org.briarproject.briar.feed.DnsModule;
import org.briarproject.briar.feed.FeedModule;
import org.briarproject.briar.forum.ForumModule;
import org.briarproject.briar.introduction.IntroductionModule;
import org.briarproject.briar.messaging.MessagingModule;
import org.briarproject.briar.privategroup.PrivateGroupModule;
import org.briarproject.briar.privategroup.invitation.GroupInvitationModule;
import org.briarproject.briar.sharing.SharingModule;

import dagger.Module;

@Module(includes = {
		BlogModule.class,
		BriarClientModule.class,
		FeedModule.class,
		DnsModule.class,
		ForumModule.class,
		GroupInvitationModule.class,
		IntroductionModule.class,
		MessagingModule.class,
		PrivateGroupModule.class,
		SharingModule.class
})
public class BriarCoreModule {

	public static void initEagerSingletons(BriarCoreEagerSingletons c) {
		c.inject(new BlogModule.EagerSingletons());
		c.inject(new FeedModule.EagerSingletons());
		c.inject(new ForumModule.EagerSingletons());
		c.inject(new GroupInvitationModule.EagerSingletons());
		c.inject(new MessagingModule.EagerSingletons());
		c.inject(new PrivateGroupModule.EagerSingletons());
		c.inject(new SharingModule.EagerSingletons());
		c.inject(new IntroductionModule.EagerSingletons());
	}
}
