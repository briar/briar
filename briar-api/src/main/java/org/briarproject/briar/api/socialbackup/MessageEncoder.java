package org.briarproject.briar.api.socialbackup;

import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.briar.api.socialbackup.Shard;

@NotNullByDefault
public interface MessageEncoder {

	byte[] encodeShardMessage(Shard shard);

	byte[] encodeBackupMessage(int version, org.briarproject.briar.api.socialbackup.BackupPayload payload);

	byte[] encodeReturnShardPayload(ReturnShardPayload returnShardPayload);
}
