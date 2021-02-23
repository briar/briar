package org.briarproject.briar.socialbackup;

import org.briarproject.bramble.api.client.ClientHelper;
import org.briarproject.bramble.api.contact.ContactManager;
import org.briarproject.bramble.api.data.MetadataEncoder;
import org.briarproject.bramble.api.lifecycle.LifecycleManager;
import org.briarproject.bramble.api.sync.validation.ValidationManager;
import org.briarproject.bramble.api.system.Clock;
import org.briarproject.bramble.api.versioning.ClientVersioningManager;
import org.briarproject.briar.api.socialbackup.SocialBackupManager;

import javax.inject.Inject;
import javax.inject.Singleton;

import dagger.Module;
import dagger.Provides;

import static org.briarproject.briar.api.socialbackup.SocialBackupManager.CLIENT_ID;
import static org.briarproject.briar.api.socialbackup.SocialBackupManager.MAJOR_VERSION;
import static org.briarproject.briar.api.socialbackup.SocialBackupManager.MINOR_VERSION;

@Module
public class SocialBackupModule {

	public static class EagerSingletons {
		@Inject
		SocialBackupManager socialBackupManager;
		@Inject
		SocialBackupValidator socialBackupValidator;
	}

	@Provides
	@Singleton
	SocialBackupManager socialBackupManager(
			LifecycleManager lifecycleManager,
			ContactManager contactManager,
			ValidationManager validationManager,
			ClientVersioningManager clientVersioningManager,
			SocialBackupManagerImpl socialBackupManager) {
		lifecycleManager.registerOpenDatabaseHook(socialBackupManager);
		contactManager.registerContactHook(socialBackupManager);
		validationManager.registerIncomingMessageHook(CLIENT_ID,
				MAJOR_VERSION, socialBackupManager);
		clientVersioningManager.registerClient(CLIENT_ID, MAJOR_VERSION,
				MINOR_VERSION, socialBackupManager);
		return socialBackupManager;
	}

	@Provides
	@Singleton
	SocialBackupValidator socialBackupValidator(
			ValidationManager validationManager,
			ClientHelper clientHelper,
			MetadataEncoder metadataEncoder,
			Clock clock) {
		SocialBackupValidator validator =
				new SocialBackupValidator(clientHelper, metadataEncoder, clock);
		validationManager.registerMessageValidator(CLIENT_ID, MAJOR_VERSION,
				validator);
		return validator;
	}

	@Provides
	BackupMetadataParser backupMetadataParser(
			BackupMetadataParserImpl backupMetadataParser) {
		return backupMetadataParser;
	}

	@Provides
	BackupMetadataEncoder backupMetadataEncoder(
			BackupMetadataEncoderImpl backupMetadataEncoder) {
		return backupMetadataEncoder;
	}

	@Provides
	BackupPayloadEncoder backupPayloadEncoder(
			BackupPayloadEncoderImpl backupPayloadEncoder) {
		return backupPayloadEncoder;
	}

	@Provides
	MessageEncoder messageEncoder(MessageEncoderImpl messageEncoder) {
		return messageEncoder;
	}

	@Provides
	MessageParser messageParser(MessageParserImpl messageParser) {
		return messageParser;
	}

	@Provides
	DarkCrystal darkCrystal(DarkCrystalStub darkCrystal) {
		// TODO: Replace this with a real implementation
		return darkCrystal;
	}
}
