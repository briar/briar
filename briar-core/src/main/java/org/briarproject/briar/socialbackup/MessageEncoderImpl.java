package org.briarproject.briar.socialbackup;

import org.briarproject.bramble.api.FormatException;
import org.briarproject.bramble.api.client.ClientHelper;
import org.briarproject.bramble.api.data.BdfList;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.briar.api.socialbackup.BackupPayload;
import org.briarproject.briar.api.socialbackup.ReturnShardPayload;
import org.briarproject.briar.api.socialbackup.Shard;

import javax.annotation.concurrent.Immutable;
import javax.inject.Inject;

import dagger.Reusable;

import static org.briarproject.briar.socialbackup.MessageType.BACKUP;
import static org.briarproject.briar.socialbackup.MessageType.SHARD;

@Immutable
@NotNullByDefault
class MessageEncoderImpl implements
		org.briarproject.briar.api.socialbackup.MessageEncoder {

	private final ClientHelper clientHelper;

	@Inject
	MessageEncoderImpl(ClientHelper clientHelper) {
		this.clientHelper = clientHelper;
	}

	@Override
	public byte[] encodeShardMessage(Shard shard) {
		BdfList body = BdfList.of(
				SHARD.getValue(),
				shard.getSecretId(),
				shard.getShard()
		);
		return encodeBody(body);
	}

	@Override
	public byte[] encodeBackupMessage(int version, org.briarproject.briar.api.socialbackup.BackupPayload payload) {
		BdfList body = BdfList.of(
				BACKUP.getValue(),
				version,
				payload.getBytes()
		);
		return encodeBody(body);
	}

	public byte[] encodeReturnShardPayload(ReturnShardPayload returnShardPayload) {
		Shard shard = returnShardPayload.getShard();
		BdfList shardList = BdfList.of(
				SHARD.getValue(),
				shard.getSecretId(),
				shard.getShard()
		);
		org.briarproject.briar.api.socialbackup.BackupPayload backupPayload = returnShardPayload.getBackupPayload();

		BdfList body = BdfList.of(
			shardList,
			returnShardPayload.getBackupPayload().getBytes()
		);

		return encodeBody(body);
	}

	private byte[] encodeBody(BdfList body) {
		try {
			return clientHelper.toByteArray(body);
		} catch (FormatException e) {
			throw new AssertionError(e);
		}
	}
}
