package org.briarproject.briar.socialbackup;

import org.briarproject.bramble.api.client.ClientHelper;
import org.briarproject.bramble.api.data.BdfDictionary;
import org.briarproject.bramble.api.data.BdfList;
import org.briarproject.bramble.api.identity.Author;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.briar.api.socialbackup.BackupMetadata;

import javax.annotation.concurrent.Immutable;
import javax.inject.Inject;

import static org.briarproject.briar.socialbackup.SocialBackupConstants.GROUP_KEY_CUSTODIANS;
import static org.briarproject.briar.socialbackup.SocialBackupConstants.GROUP_KEY_SECRET;
import static org.briarproject.briar.socialbackup.SocialBackupConstants.GROUP_KEY_THRESHOLD;
import static org.briarproject.briar.socialbackup.SocialBackupConstants.GROUP_KEY_VERSION;

@Immutable
@NotNullByDefault
class BackupMetadataEncoderImpl implements BackupMetadataEncoder {

	private final ClientHelper clientHelper;

	@Inject
	BackupMetadataEncoderImpl(ClientHelper clientHelper) {
		this.clientHelper = clientHelper;
	}

	@Override
	public BdfDictionary encodeBackupMetadata(BackupMetadata backupMetadata) {
		BdfList custodians = new BdfList();
		for (Author custodian : backupMetadata.getCustodians()) {
			custodians.add(clientHelper.toList(custodian));
		}
		BdfDictionary meta = new BdfDictionary();
		meta.put(GROUP_KEY_SECRET, backupMetadata.getSecret().getBytes());
		meta.put(GROUP_KEY_CUSTODIANS, custodians);
		meta.put(GROUP_KEY_THRESHOLD, backupMetadata.getThreshold());
		meta.put(GROUP_KEY_VERSION, backupMetadata.getVersion());
		return meta;
	}
}
