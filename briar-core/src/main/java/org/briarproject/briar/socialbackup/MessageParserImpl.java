package org.briarproject.briar.socialbackup;

import org.briarproject.bramble.api.FormatException;
import org.briarproject.bramble.api.data.BdfList;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.briar.api.socialbackup.BackupPayload;
import org.briarproject.briar.api.socialbackup.ReturnShardPayload;
import org.briarproject.briar.api.socialbackup.Shard;

import javax.annotation.concurrent.Immutable;
import javax.inject.Inject;

import static org.briarproject.bramble.util.ValidationUtils.checkSize;

@Immutable
@NotNullByDefault
class MessageParserImpl implements
		org.briarproject.briar.api.socialbackup.MessageParser {

	@Inject
	MessageParserImpl() {
	}

	@Override
	public Shard parseShardMessage(BdfList body) throws FormatException {
		// Message type, secret ID, shard
		byte[] secretId = body.getRaw(1);
		byte[] shard = body.getRaw(2);
		return new Shard(secretId, shard);
	}

	@Override
	public org.briarproject.briar.api.socialbackup.BackupPayload parseBackupMessage(BdfList body)
			throws FormatException {
		// Message type, version, backup payload
		return new org.briarproject.briar.api.socialbackup.BackupPayload(body.getRaw(2));
	}

	@Override
	public ReturnShardPayload parseReturnShardPayload(BdfList body)
			throws FormatException {
		checkSize(body, 2);
		Shard shard = parseShardMessage(body.getList(0));
		org.briarproject.briar.api.socialbackup.BackupPayload backupPayload = new BackupPayload(body.getRaw(1));
		return new ReturnShardPayload(shard, backupPayload);
	}
}
