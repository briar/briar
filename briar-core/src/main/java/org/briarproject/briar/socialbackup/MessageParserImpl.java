package org.briarproject.briar.socialbackup;

import org.briarproject.bramble.api.FormatException;
import org.briarproject.bramble.api.client.ClientHelper;
import org.briarproject.bramble.api.data.BdfList;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.briar.api.socialbackup.Shard;

import javax.annotation.concurrent.Immutable;
import javax.inject.Inject;

@Immutable
@NotNullByDefault
class MessageParserImpl implements MessageParser {

	private final ClientHelper clientHelper;

	@Inject
	MessageParserImpl(ClientHelper clientHelper) {
		this.clientHelper = clientHelper;
	}

	@Override
	public Shard parseShardMessage(byte[] body) throws FormatException {
		BdfList list = clientHelper.toList(body);
		// Message type, secret ID, num shards, threshold, shard
		byte[] secretId = list.getRaw(1);
		int numShards = list.getLong(2).intValue();
		int threshold = list.getLong(3).intValue();
		byte[] shard = list.getRaw(4);
		return new Shard(secretId, numShards, threshold, shard);
	}

	@Override
	public BackupPayload parseBackupMessage(byte[] body)
			throws FormatException {
		BdfList list = clientHelper.toList(body);
		// Message type, backup payload
		return new BackupPayload(list.getRaw(1));
	}
}
