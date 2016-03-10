package org.briarproject;

import org.briarproject.api.android.PlatformExecutor;
import org.briarproject.api.android.ReferenceManager;
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
import org.briarproject.api.lifecycle.IoExecutor;
import org.briarproject.api.lifecycle.LifecycleManager;
import org.briarproject.api.messaging.MessagingManager;
import org.briarproject.api.messaging.PrivateMessageFactory;
import org.briarproject.api.plugins.ConnectionRegistry;
import org.briarproject.api.plugins.PluginManager;
import org.briarproject.api.properties.TransportPropertyManager;
import org.briarproject.api.settings.SettingsManager;
import org.briarproject.api.sync.ValidationManager;
import org.briarproject.api.transport.KeyManager;
import org.briarproject.clients.ClientsModule;
import org.briarproject.contact.ContactModule;
import org.briarproject.crypto.CryptoModule;
import org.briarproject.data.DataModule;
import org.briarproject.db.DatabaseModule;
import org.briarproject.event.EventModule;
import org.briarproject.forum.ForumModule;
import org.briarproject.identity.IdentityModule;
import org.briarproject.invitation.InvitationModule;
import org.briarproject.lifecycle.LifecycleModule;
import org.briarproject.messaging.MessagingModule;
import org.briarproject.messaging.PrivateMessageValidator;
import org.briarproject.plugins.PluginsModule;
import org.briarproject.properties.PropertiesModule;
import org.briarproject.properties.TransportPropertyValidator;
import org.briarproject.reliability.ReliabilityModule;
import org.briarproject.settings.SettingsModule;
import org.briarproject.sync.SyncModule;
import org.briarproject.system.SystemModule;
import org.briarproject.transport.TransportModule;

import java.util.concurrent.Executor;

import javax.inject.Singleton;

import dagger.Component;

@Singleton
@Component(modules = {DatabaseModule.class,
		CryptoModule.class, LifecycleModule.class, PlatformModule.class,
		ReliabilityModule.class, MessagingModule.class,
		InvitationModule.class, ForumModule.class, IdentityModule.class,
		EventModule.class, DataModule.class, ContactModule.class,
		PropertiesModule.class, TransportModule.class, SyncModule.class,
		SettingsModule.class, ClientsModule.class, SystemModule.class,
		PluginsModule.class})
public interface CoreComponent {
	@IoExecutor
	Executor ioExecutor();
	ContactManager contactManager();
	@CryptoExecutor Executor cryptoExecutor();
	DatabaseConfig databaseConfig();
	PasswordStrengthEstimator passwordStrengthEstimator();
	CryptoComponent cryptoComponent();
	@DatabaseExecutor Executor dbExecutor();
	LifecycleManager lifecycleManager();
	AuthorFactory authFactory();
	EventBus eventBus();
	KeyManager keyManager();
	ValidationManager validationManager();
	ForumManager forumManager();
	IdentityManager identityManager();
	PluginManager pluginManager();
	SettingsManager settingsManater();
	InvitationTaskFactory invitationTaskFactory();
	MessagingManager messagingManager();
	TransportPropertyManager transportPropertyManager();
	ConnectionRegistry connectionRegistry();
	ForumSharingManager forumSharingManager();
	PrivateMessageFactory privateMessageFactory();
	ForumPostFactory forumPostFactory();
	PrivateMessageValidator privateMessageValidator();
	TransportPropertyValidator transportPropertyValidator();
	PlatformExecutor platformExecutor();
	// Eager singletons
	void inject(ContactModule.EagerSingletons init);
	void inject(CryptoModule.EagerSingletons init);
	void inject(DatabaseModule.EagerSingletons init);
	void inject(ForumModule.EagerSingletons init);
	void inject(LifecycleModule.EagerSingletons init);
	void inject(MessagingModule.EagerSingletons init);
	void inject(PluginsModule.EagerSingletons init);
	void inject(PropertiesModule.EagerSingletons init);
	void inject(SyncModule.EagerSingletons init);
	void inject(TransportModule.EagerSingletons init);
}
