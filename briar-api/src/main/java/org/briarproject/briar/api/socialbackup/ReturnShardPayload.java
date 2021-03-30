package org.briarproject.briar.api.socialbackup;

import org.briarproject.bramble.api.nullsafety.NotNullByDefault;

import javax.annotation.concurrent.Immutable;

@Immutable
@NotNullByDefault
public class ReturnShardPayload {
	private final Shard shard;
	private final BackupPayload backupPayload;

	public ReturnShardPayload(Shard shard, BackupPayload backupPayload) {
		this.shard = shard;
		this.backupPayload = backupPayload;
	}

	public Shard getShard() {
		return shard;
	}

	public BackupPayload getBackupPayload() {
		return backupPayload;
	}
}
