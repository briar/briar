package org.briarproject.bramble.mailbox;

import org.briarproject.bramble.api.FeatureFlags;
import org.briarproject.bramble.api.client.ClientHelper;
import org.briarproject.bramble.api.contact.ContactManager;
import org.briarproject.bramble.api.data.MetadataEncoder;
import org.briarproject.bramble.api.db.DatabaseExecutor;
import org.briarproject.bramble.api.db.TransactionManager;
import org.briarproject.bramble.api.event.EventBus;
import org.briarproject.bramble.api.event.EventExecutor;
import org.briarproject.bramble.api.lifecycle.LifecycleManager;
import org.briarproject.bramble.api.mailbox.MailboxManager;
import org.briarproject.bramble.api.mailbox.MailboxSettingsManager;
import org.briarproject.bramble.api.mailbox.MailboxUpdateManager;
import org.briarproject.bramble.api.mailbox.MailboxVersion;
import org.briarproject.bramble.api.plugin.PluginManager;
import org.briarproject.bramble.api.sync.validation.ValidationManager;
import org.briarproject.bramble.api.system.Clock;
import org.briarproject.bramble.api.versioning.ClientVersioningManager;

import java.util.List;
import java.util.concurrent.Executor;

import javax.inject.Inject;
import javax.inject.Singleton;

import dagger.Module;
import dagger.Provides;

import static org.briarproject.bramble.api.mailbox.MailboxConstants.CLIENT_SUPPORTS;
import static org.briarproject.bramble.api.mailbox.MailboxUpdateManager.CLIENT_ID;
import static org.briarproject.bramble.api.mailbox.MailboxUpdateManager.MAJOR_VERSION;
import static org.briarproject.bramble.api.mailbox.MailboxUpdateManager.MINOR_VERSION;

@Module
public class MailboxModule {

	public static class EagerSingletons {
		@Inject
		MailboxUpdateValidator mailboxUpdateValidator;
		@Inject
		MailboxUpdateManager mailboxUpdateManager;
		@Inject
		MailboxFileManager mailboxFileManager;
		@Inject
		MailboxClientManager mailboxClientManager;
	}

	@Provides
	@Singleton
	MailboxManager providesMailboxManager(MailboxManagerImpl mailboxManager) {
		return mailboxManager;
	}

	@Provides
	MailboxPairingTaskFactory provideMailboxPairingTaskFactory(
			MailboxPairingTaskFactoryImpl mailboxPairingTaskFactory) {
		return mailboxPairingTaskFactory;
	}

	@Provides
	@Singleton
	MailboxSettingsManager provideMailboxSettingsManager(
			MailboxSettingsManagerImpl mailboxSettingsManager) {
		return mailboxSettingsManager;
	}

	@Provides
	MailboxApi provideMailboxApi(MailboxApiImpl mailboxApi) {
		return mailboxApi;
	}

	@Provides
	@Singleton
	MailboxUpdateValidator provideMailboxUpdateValidator(
			ValidationManager validationManager,
			ClientHelper clientHelper,
			MetadataEncoder metadataEncoder,
			Clock clock,
			FeatureFlags featureFlags) {
		MailboxUpdateValidator validator = new MailboxUpdateValidator(
				clientHelper, metadataEncoder, clock);
		if (featureFlags.shouldEnableMailbox()) {
			validationManager.registerMessageValidator(CLIENT_ID,
					MAJOR_VERSION, validator);
		}
		return validator;
	}

	@Provides
	List<MailboxVersion> provideClientSupports() {
		return CLIENT_SUPPORTS;
	}

	@Provides
	@Singleton
	MailboxUpdateManager provideMailboxUpdateManager(
			FeatureFlags featureFlags,
			LifecycleManager lifecycleManager,
			ValidationManager validationManager, ContactManager contactManager,
			ClientVersioningManager clientVersioningManager,
			MailboxSettingsManager mailboxSettingsManager,
			MailboxUpdateManagerImpl mailboxUpdateManager) {
		if (featureFlags.shouldEnableMailbox()) {
			lifecycleManager.registerOpenDatabaseHook(mailboxUpdateManager);
			validationManager.registerIncomingMessageHook(CLIENT_ID,
					MAJOR_VERSION, mailboxUpdateManager);
			contactManager.registerContactHook(mailboxUpdateManager);
			clientVersioningManager.registerClient(CLIENT_ID, MAJOR_VERSION,
					MINOR_VERSION, mailboxUpdateManager);
			mailboxSettingsManager.registerMailboxHook(mailboxUpdateManager);
		}
		return mailboxUpdateManager;
	}

	@Provides
	@Singleton
	MailboxFileManager provideMailboxFileManager(FeatureFlags featureFlags,
			EventBus eventBus, MailboxFileManagerImpl mailboxFileManager) {
		if (featureFlags.shouldEnableMailbox()) {
			eventBus.addListener(mailboxFileManager);
		}
		return mailboxFileManager;
	}

	@Provides
	MailboxWorkerFactory provideMailboxWorkerFactory(
			MailboxWorkerFactoryImpl mailboxWorkerFactory) {
		return mailboxWorkerFactory;
	}

	@Provides
	MailboxClientFactory provideMailboxClientFactory(
			MailboxClientFactoryImpl mailboxClientFactory) {
		return mailboxClientFactory;
	}

	@Provides
	MailboxApiCaller provideMailboxApiCaller(
			MailboxApiCallerImpl mailboxApiCaller) {
		return mailboxApiCaller;
	}

	@Provides
	@Singleton
	TorReachabilityMonitor provideTorReachabilityMonitor(
			TorReachabilityMonitorImpl reachabilityMonitor) {
		return reachabilityMonitor;
	}

	@Provides
	@Singleton
	MailboxClientManager provideMailboxClientManager(
			@EventExecutor Executor eventExecutor,
			@DatabaseExecutor Executor dbExecutor,
			TransactionManager db,
			ContactManager contactManager,
			PluginManager pluginManager,
			MailboxSettingsManager mailboxSettingsManager,
			MailboxUpdateManager mailboxUpdateManager,
			MailboxClientFactory mailboxClientFactory,
			TorReachabilityMonitor reachabilityMonitor,
			FeatureFlags featureFlags,
			LifecycleManager lifecycleManager,
			EventBus eventBus) {
		MailboxClientManager manager = new MailboxClientManager(eventExecutor,
				dbExecutor, db, contactManager, pluginManager,
				mailboxSettingsManager, mailboxUpdateManager,
				mailboxClientFactory, reachabilityMonitor);
		if (featureFlags.shouldEnableMailbox()) {
			lifecycleManager.registerService(manager);
			eventBus.addListener(manager);
		}
		return manager;
	}
}
