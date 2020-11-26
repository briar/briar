package org.briarproject.briar.introduction;

import org.briarproject.bramble.BrambleCoreModule;
import org.briarproject.bramble.test.BrambleCoreIntegrationTestModule;
import org.briarproject.briar.autodelete.AutoDeleteModule;
import org.briarproject.briar.blog.BlogModule;
import org.briarproject.briar.client.BriarClientModule;
import org.briarproject.briar.forum.ForumModule;
import org.briarproject.briar.messaging.MessagingModule;
import org.briarproject.briar.privategroup.PrivateGroupModule;
import org.briarproject.briar.privategroup.invitation.GroupInvitationModule;
import org.briarproject.briar.sharing.SharingModule;
import org.briarproject.briar.test.BriarIntegrationTestComponent;

import javax.inject.Singleton;

import dagger.Component;

@Singleton
@Component(modules = {
		AutoDeleteModule.class,
		BrambleCoreIntegrationTestModule.class,
		BrambleCoreModule.class,
		BlogModule.class,
		BriarClientModule.class,
		ForumModule.class,
		GroupInvitationModule.class,
		IntroductionModule.class,
		MessagingModule.class,
		PrivateGroupModule.class,
		SharingModule.class
})
interface IntroductionIntegrationTestComponent
		extends BriarIntegrationTestComponent {

	void inject(IntroductionIntegrationTest init);

	void inject(MessageEncoderParserIntegrationTest init);

	void inject(SessionEncoderParserIntegrationTest init);

	void inject(IntroductionCryptoIntegrationTest init);

	MessageEncoder getMessageEncoder();

	MessageParser getMessageParser();

	SessionParser getSessionParser();

	IntroductionCrypto getIntroductionCrypto();

}
