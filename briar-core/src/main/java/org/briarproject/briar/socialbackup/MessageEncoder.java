package org.briarproject.briar.socialbackup;

import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.briar.api.socialbackup.Shard;

@NotNullByDefault
interface MessageEncoder {

	byte[] encodeShardMessage(Shard shard);

	byte[] encodeBackupMessage(int version, BackupPayload payload);
}
