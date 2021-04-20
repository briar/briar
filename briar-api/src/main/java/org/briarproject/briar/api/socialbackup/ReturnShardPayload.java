package org.briarproject.briar.api.socialbackup;

import org.briarproject.bramble.api.FormatException;
import org.briarproject.bramble.api.data.BdfList;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;

import javax.annotation.concurrent.Immutable;

import static org.briarproject.bramble.util.ValidationUtils.checkSize;

@Immutable
@NotNullByDefault
public class ReturnShardPayload {
	private final Shard shard;
	private final BackupPayload backupPayload;

	// TODO this does not belong here
	private static Shard parseShardMessage(BdfList body) throws FormatException {
		// Message type, secret ID, shard
		byte[] secretId = body.getRaw(1);
		byte[] shard = body.getRaw(2);
		return new Shard(secretId, shard);
	}

	public static ReturnShardPayload fromList(BdfList body) throws FormatException {
		checkSize(body, 2);
		Shard shard = parseShardMessage(body.getList(0));
		BackupPayload backupPayload = new BackupPayload(body.getRaw(1));
		return new ReturnShardPayload(shard, backupPayload);
	}

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

	public boolean equals(ReturnShardPayload otherReturnShardPayload) {
		return shard.equals(otherReturnShardPayload.getShard()) && backupPayload
				.equals(otherReturnShardPayload.getBackupPayload());
	}
}
