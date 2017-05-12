package org.briarproject.briar.android;

import org.briarproject.bramble.BrambleAndroidModule;
import org.briarproject.bramble.BrambleCoreEagerSingletons;
import org.briarproject.bramble.BrambleCoreModule;
import org.briarproject.bramble.api.contact.ContactExchangeTask;
import org.briarproject.bramble.api.contact.ContactManager;
import org.briarproject.bramble.api.crypto.CryptoComponent;
import org.briarproject.bramble.api.crypto.CryptoExecutor;
import org.briarproject.bramble.api.crypto.PasswordStrengthEstimator;
import org.briarproject.bramble.api.db.DatabaseConfig;
import org.briarproject.bramble.api.db.DatabaseExecutor;
import org.briarproject.bramble.api.event.EventBus;
import org.briarproject.bramble.api.identity.IdentityManager;
import org.briarproject.bramble.api.invitation.InvitationTaskFactory;
import org.briarproject.bramble.api.keyagreement.KeyAgreementTaskFactory;
import org.briarproject.bramble.api.keyagreement.PayloadEncoder;
import org.briarproject.bramble.api.keyagreement.PayloadParser;
import org.briarproject.bramble.api.lifecycle.IoExecutor;
import org.briarproject.bramble.api.lifecycle.LifecycleManager;
import org.briarproject.bramble.api.plugin.ConnectionRegistry;
import org.briarproject.bramble.api.plugin.PluginManager;
import org.briarproject.bramble.api.settings.SettingsManager;
import org.briarproject.bramble.api.system.AndroidExecutor;
import org.briarproject.bramble.api.system.Clock;
import org.briarproject.briar.BriarCoreEagerSingletons;
import org.briarproject.briar.BriarCoreModule;
import org.briarproject.briar.android.reporting.BriarReportSender;
import org.briarproject.briar.api.android.AndroidNotificationManager;
import org.briarproject.briar.api.android.ReferenceManager;
import org.briarproject.briar.api.android.ScreenFilterMonitor;
import org.briarproject.briar.api.blog.BlogManager;
import org.briarproject.briar.api.blog.BlogPostFactory;
import org.briarproject.briar.api.blog.BlogSharingManager;
import org.briarproject.briar.api.client.MessageTracker;
import org.briarproject.briar.api.feed.FeedManager;
import org.briarproject.briar.api.forum.ForumManager;
import org.briarproject.briar.api.forum.ForumSharingManager;
import org.briarproject.briar.api.introduction.IntroductionManager;
import org.briarproject.briar.api.messaging.ConversationManager;
import org.briarproject.briar.api.messaging.MessagingManager;
import org.briarproject.briar.api.messaging.PrivateMessageFactory;
import org.briarproject.briar.api.privategroup.GroupMessageFactory;
import org.briarproject.briar.api.privategroup.PrivateGroupFactory;
import org.briarproject.briar.api.privategroup.PrivateGroupManager;
import org.briarproject.briar.api.privategroup.invitation.GroupInvitationFactory;
import org.briarproject.briar.api.privategroup.invitation.GroupInvitationManager;
import org.thoughtcrime.securesms.components.emoji.EmojiProvider;
import org.thoughtcrime.securesms.components.emoji.RecentEmojiPageModel;

import java.util.concurrent.Executor;

import javax.inject.Singleton;

import dagger.Component;

@Singleton
@Component(modules = {
		BrambleCoreModule.class,
		BriarCoreModule.class,
		BrambleAndroidModule.class,
		AppModule.class
})
public interface AndroidComponent
		extends BrambleCoreEagerSingletons, BriarCoreEagerSingletons {

	// Exposed objects
	@CryptoExecutor
	Executor cryptoExecutor();

	PasswordStrengthEstimator passwordStrengthIndicator();

	CryptoComponent cryptoComponent();

	DatabaseConfig databaseConfig();

	ReferenceManager referenceMangager();

	@DatabaseExecutor
	Executor databaseExecutor();

	MessageTracker messageTracker();

	LifecycleManager lifecycleManager();

	IdentityManager identityManager();

	PluginManager pluginManager();

	EventBus eventBus();

	InvitationTaskFactory invitationTaskFactory();

	AndroidNotificationManager androidNotificationManager();

	ScreenFilterMonitor screenFilterMonitor();

	ConnectionRegistry connectionRegistry();

	ContactManager contactManager();

	ConversationManager conversationManager();

	MessagingManager messagingManager();

	PrivateMessageFactory privateMessageFactory();

	PrivateGroupManager privateGroupManager();

	GroupInvitationFactory groupInvitationFactory();

	GroupInvitationManager groupInvitationManager();

	PrivateGroupFactory privateGroupFactory();

	GroupMessageFactory groupMessageFactory();

	ForumManager forumManager();

	ForumSharingManager forumSharingManager();

	BlogSharingManager blogSharingManager();

	BlogManager blogManager();

	BlogPostFactory blogPostFactory();

	SettingsManager settingsManager();

	ContactExchangeTask contactExchangeTask();

	KeyAgreementTaskFactory keyAgreementTaskFactory();

	PayloadEncoder payloadEncoder();

	PayloadParser payloadParser();

	IntroductionManager introductionManager();

	AndroidExecutor androidExecutor();

	FeedManager feedManager();

	Clock clock();

	@IoExecutor
	Executor ioExecutor();

	void inject(BriarService briarService);

	void inject(BriarReportSender briarReportSender);

	void inject(EmojiProvider emojiProvider);

	void inject(RecentEmojiPageModel recentEmojiPageModel);

	// Eager singleton load
	void inject(AppModule.EagerSingletons init);
}
