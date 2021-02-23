package org.briarproject.briar.socialbackup;

import org.briarproject.bramble.api.FormatException;
import org.briarproject.bramble.api.data.BdfDictionary;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.briar.api.socialbackup.BackupMetadata;

import javax.annotation.Nullable;

@NotNullByDefault
interface BackupMetadataParser {

	@Nullable
	BackupMetadata parseBackupMetadata(BdfDictionary meta)
			throws FormatException;
}
