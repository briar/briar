package org.briarproject.briar.socialbackup;

import org.briarproject.bramble.api.FormatException;
import org.briarproject.bramble.api.data.BdfList;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.briar.api.socialbackup.Shard;

import javax.annotation.concurrent.Immutable;
import javax.inject.Inject;

@Immutable
@NotNullByDefault
class MessageParserImpl implements MessageParser {

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
		// Message type, backup payload
		return new org.briarproject.briar.api.socialbackup.BackupPayload(body.getRaw(1));
	}
}
