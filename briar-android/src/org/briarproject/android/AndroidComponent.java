package org.briarproject.android;

import org.briarproject.CoreEagerSingletons;
import org.briarproject.CoreModule;
import org.briarproject.android.api.AndroidExecutor;
import org.briarproject.android.api.AndroidNotificationManager;
import org.briarproject.android.api.ReferenceManager;
import org.briarproject.android.blogs.BlogPersistentData;
import org.briarproject.android.forum.ForumPersistentData;
import org.briarproject.android.report.BriarReportSender;
import org.briarproject.api.blogs.BlogManager;
import org.briarproject.api.blogs.BlogPostFactory;
import org.briarproject.api.contact.ContactExchangeTask;
import org.briarproject.api.contact.ContactManager;
import org.briarproject.api.crypto.CryptoComponent;
import org.briarproject.api.crypto.CryptoExecutor;
import org.briarproject.api.crypto.PasswordStrengthEstimator;
import org.briarproject.api.db.DatabaseConfig;
import org.briarproject.api.db.DatabaseExecutor;
import org.briarproject.api.event.EventBus;
import org.briarproject.api.forum.ForumManager;
import org.briarproject.api.forum.ForumPostFactory;
import org.briarproject.api.forum.ForumSharingManager;
import org.briarproject.api.identity.AuthorFactory;
import org.briarproject.api.identity.IdentityManager;
import org.briarproject.api.introduction.IntroductionManager;
import org.briarproject.api.invitation.InvitationTaskFactory;
import org.briarproject.api.keyagreement.KeyAgreementTaskFactory;
import org.briarproject.api.keyagreement.PayloadEncoder;
import org.briarproject.api.keyagreement.PayloadParser;
import org.briarproject.api.lifecycle.IoExecutor;
import org.briarproject.api.lifecycle.LifecycleManager;
import org.briarproject.api.messaging.MessagingManager;
import org.briarproject.api.messaging.PrivateMessageFactory;
import org.briarproject.api.plugins.ConnectionRegistry;
import org.briarproject.api.plugins.PluginManager;
import org.briarproject.api.properties.TransportPropertyManager;
import org.briarproject.api.settings.SettingsManager;
import org.briarproject.plugins.AndroidPluginsModule;
import org.briarproject.system.AndroidSystemModule;

import java.util.concurrent.Executor;

import javax.inject.Singleton;

import dagger.Component;

@Singleton
@Component(modules = {
		CoreModule.class,
		AppModule.class,
		AndroidPluginsModule.class,
		AndroidSystemModule.class
})
public interface AndroidComponent extends CoreEagerSingletons {

	// Exposed objects
	@CryptoExecutor
	Executor cryptoExecutor();

	PasswordStrengthEstimator passwordStrengthIndicator();

	CryptoComponent cryptoComponent();

	DatabaseConfig databaseConfig();

	AuthorFactory authFactory();

	ReferenceManager referenceMangager();

	@DatabaseExecutor
	Executor databaseExecutor();

	LifecycleManager lifecycleManager();

	IdentityManager identityManager();

	PluginManager pluginManager();

	EventBus eventBus();

	InvitationTaskFactory invitationTaskFactory();

	AndroidNotificationManager androidNotificationManager();

	ConnectionRegistry connectionRegistry();

	ContactManager contactManager();

	MessagingManager messagingManager();

	PrivateMessageFactory privateMessageFactory();

	TransportPropertyManager transportPropertyManager();

	ForumManager forumManager();

	ForumSharingManager forumSharingManager();

	ForumPostFactory forumPostFactory();

	BlogManager blogManager();

	BlogPostFactory blogPostFactory();

	SettingsManager settingsManager();

	ContactExchangeTask contactExchangeTask();

	KeyAgreementTaskFactory keyAgreementTaskFactory();

	PayloadEncoder payloadEncoder();

	PayloadParser payloadParser();

	IntroductionManager introductionManager();

	AndroidExecutor androidExecutor();

	ForumPersistentData forumPersistentData();

	BlogPersistentData blogPersistentData();

	@IoExecutor
	Executor ioExecutor();

	void inject(BriarService activity);

	void inject(BriarReportSender briarReportSender);

	// Eager singleton load
	void inject(AppModule.EagerSingletons init);
}
