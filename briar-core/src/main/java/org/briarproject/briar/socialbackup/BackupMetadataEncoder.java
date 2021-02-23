package org.briarproject.briar.socialbackup;

import org.briarproject.bramble.api.data.BdfDictionary;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.briar.api.socialbackup.BackupMetadata;

@NotNullByDefault
interface BackupMetadataEncoder {

	BdfDictionary encodeBackupMetadata(BackupMetadata backupMetadata);
}
