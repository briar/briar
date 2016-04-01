package org.briarproject.android;

import org.briarproject.CoreEagerSingletons;
import org.briarproject.CoreModule;
import org.briarproject.android.api.AndroidNotificationManager;
import org.briarproject.android.api.ReferenceManager;
import org.briarproject.android.contact.ContactListFragment;
import org.briarproject.android.contact.ConversationActivity;
import org.briarproject.android.event.AppBus;
import org.briarproject.android.forum.AvailableForumsActivity;
import org.briarproject.android.forum.ContactSelectorFragment;
import org.briarproject.android.forum.CreateForumActivity;
import org.briarproject.android.forum.ForumActivity;
import org.briarproject.android.forum.ForumListFragment;
import org.briarproject.android.forum.ReadForumPostActivity;
import org.briarproject.android.forum.ShareForumActivity;
import org.briarproject.android.forum.ShareForumMessageFragment;
import org.briarproject.android.forum.WriteForumPostActivity;
import org.briarproject.android.identity.CreateIdentityActivity;
import org.briarproject.android.introduction.ContactChooserFragment;
import org.briarproject.android.introduction.IntroductionActivity;
import org.briarproject.android.introduction.IntroductionMessageFragment;
import org.briarproject.android.invitation.AddContactActivity;
import org.briarproject.android.keyagreement.ChooseIdentityFragment;
import org.briarproject.android.keyagreement.KeyAgreementActivity;
import org.briarproject.android.keyagreement.ShowQrCodeFragment;
import org.briarproject.android.panic.PanicPreferencesActivity;
import org.briarproject.android.panic.PanicResponderActivity;
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
import org.briarproject.api.invitation.InvitationTaskFactory;
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

import javax.inject.Named;
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
	@CryptoExecutor Executor cryptoExecutor();
	PasswordStrengthEstimator passwordStrengthIndicator();
	CryptoComponent cryptoComponent();
	DatabaseConfig databaseConfig();
	AuthorFactory authFactory();
	ReferenceManager referenceMangager();
	@DatabaseExecutor Executor databaseExecutor();
	LifecycleManager lifecycleManager();
	IdentityManager identityManager();
	PluginManager pluginManager();
	EventBus eventBus();
	AppBus appEventBus();
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
	SettingsManager settingsManager();

	void inject(BriarService activity);

	void inject(PanicResponderActivity activity);

	void inject(PanicPreferencesActivity activity);

	void inject(AddContactActivity activity);

	void inject(KeyAgreementActivity activity);

	void inject(ConversationActivity activity);

	void inject(CreateIdentityActivity activity);

	void inject(TestingActivity activity);

	void inject(AvailableForumsActivity activity);

	void inject(WriteForumPostActivity activity);

	void inject(CreateForumActivity activity);

	void inject(ShareForumActivity activity);

	void inject(ContactSelectorFragment fragment);

	void inject(ShareForumMessageFragment fragment);

	void inject(ReadForumPostActivity activity);

	void inject(ForumActivity activity);

	void inject(SettingsActivity activity);

	void inject(ContactListFragment fragment);

	void inject(ForumListFragment fragment);

	void inject(ChooseIdentityFragment fragment);

	void inject(ShowQrCodeFragment fragment);

	void inject(IntroductionActivity activity);

	void inject(ContactChooserFragment fragment);

	void inject(IntroductionMessageFragment fragment);

	// Eager singleton load
	void inject(AppModule.EagerSingletons init);

	void inject(BriarReportSender briarReportSender);
}
