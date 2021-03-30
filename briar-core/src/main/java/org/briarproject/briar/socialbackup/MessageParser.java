package org.briarproject.briar.socialbackup;

import org.briarproject.bramble.api.FormatException;
import org.briarproject.bramble.api.data.BdfList;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.briar.api.socialbackup.Shard;

@NotNullByDefault
interface MessageParser {

	Shard parseShardMessage(BdfList body) throws FormatException;

	org.briarproject.briar.api.socialbackup.BackupPayload parseBackupMessage(BdfList body) throws FormatException;
}
