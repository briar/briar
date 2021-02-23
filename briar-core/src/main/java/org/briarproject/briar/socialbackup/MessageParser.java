package org.briarproject.briar.socialbackup;

import org.briarproject.bramble.api.FormatException;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.briar.api.socialbackup.Shard;

@NotNullByDefault
interface MessageParser {

	Shard parseShardMessage(byte[] body) throws FormatException;

	BackupPayload parseBackupMessage(byte[] body) throws FormatException;
}
