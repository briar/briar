package org.briarproject.briar;

import org.briarproject.briar.blog.BlogModule;
import org.briarproject.briar.client.BriarClientModule;
import org.briarproject.briar.feed.DnsModule;
import org.briarproject.briar.feed.FeedModule;
import org.briarproject.briar.forum.ForumModule;
import org.briarproject.briar.identity.IdentityModule;
import org.briarproject.briar.introduction.IntroductionModule;
import org.briarproject.briar.messaging.MessagingModule;
import org.briarproject.briar.privategroup.PrivateGroupModule;
import org.briarproject.briar.privategroup.invitation.GroupInvitationModule;
import org.briarproject.briar.sharing.SharingModule;
import org.briarproject.briar.test.TestModule;

import dagger.Module;

@Module(includes = {
		BlogModule.class,
		BriarClientModule.class,
		FeedModule.class,
		DnsModule.class,
		ForumModule.class,
		GroupInvitationModule.class,
		IdentityModule.class,
		IntroductionModule.class,
		MessagingModule.class,
		PrivateGroupModule.class,
		SharingModule.class,
		TestModule.class
})
public class BriarCoreModule {
}
