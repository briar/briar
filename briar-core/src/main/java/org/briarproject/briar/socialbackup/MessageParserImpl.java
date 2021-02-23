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
		// Message type, secret ID, num shards, threshold, shard
		byte[] secretId = body.getRaw(1);
		int numShards = body.getLong(2).intValue();
		int threshold = body.getLong(3).intValue();
		byte[] shard = body.getRaw(4);
		return new Shard(secretId, numShards, threshold, shard);
	}

	@Override
	public BackupPayload parseBackupMessage(BdfList body)
			throws FormatException {
		// Message type, backup payload
		return new BackupPayload(body.getRaw(1));
	}
}
