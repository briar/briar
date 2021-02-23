package org.briarproject.briar.socialbackup;

import org.briarproject.bramble.api.FormatException;
import org.briarproject.bramble.api.client.BdfMessageContext;
import org.briarproject.bramble.api.client.BdfMessageValidator;
import org.briarproject.bramble.api.client.ClientHelper;
import org.briarproject.bramble.api.data.BdfDictionary;
import org.briarproject.bramble.api.data.BdfEntry;
import org.briarproject.bramble.api.data.BdfList;
import org.briarproject.bramble.api.data.MetadataEncoder;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.sync.Group;
import org.briarproject.bramble.api.sync.Message;
import org.briarproject.bramble.api.system.Clock;

import javax.annotation.concurrent.Immutable;
import javax.inject.Inject;

import static org.briarproject.bramble.util.ValidationUtils.checkLength;
import static org.briarproject.bramble.util.ValidationUtils.checkSize;
import static org.briarproject.briar.socialbackup.MessageType.BACKUP;
import static org.briarproject.briar.socialbackup.MessageType.SHARD;
import static org.briarproject.briar.socialbackup.SocialBackupConstants.MAX_SHARD_BYTES;
import static org.briarproject.briar.socialbackup.SocialBackupConstants.MSG_KEY_LOCAL;
import static org.briarproject.briar.socialbackup.SocialBackupConstants.MSG_KEY_MESSAGE_TYPE;
import static org.briarproject.briar.socialbackup.SocialBackupConstants.MSG_KEY_VERSION;
import static org.briarproject.briar.socialbackup.SocialBackupConstants.SECRET_ID_BYTES;

@Immutable
@NotNullByDefault
class SocialBackupValidator extends BdfMessageValidator {

	@Inject
	SocialBackupValidator(ClientHelper clientHelper,
			MetadataEncoder metadataEncoder, Clock clock) {
		super(clientHelper, metadataEncoder, clock);
	}

	@Override
	protected BdfMessageContext validateMessage(Message m, Group g,
			BdfList body) throws FormatException {
		MessageType type = MessageType.fromValue(body.getLong(0).intValue());
		if (type == SHARD) return validateShardMessage(body);
		else if (type == BACKUP) return validateBackupMessage(body);
		else throw new AssertionError();
	}

	private BdfMessageContext validateShardMessage(BdfList body)
			throws FormatException {
		// Message type, secret ID, num shards, threshold, shard
		checkSize(body, 5);
		byte[] secretId = body.getRaw(1);
		checkLength(secretId, SECRET_ID_BYTES);
		int numShards = body.getLong(2).intValue();
		if (numShards < 2) throw new FormatException();
		int threshold = body.getLong(3).intValue();
		if (threshold < 2) throw new FormatException();
		if (threshold > numShards) throw new FormatException();
		byte[] shard = body.getRaw(4);
		checkLength(shard, 1, MAX_SHARD_BYTES);
		BdfDictionary meta = BdfDictionary.of(
				new BdfEntry(MSG_KEY_MESSAGE_TYPE, SHARD.getValue()),
				new BdfEntry(MSG_KEY_LOCAL, false));
		return new BdfMessageContext(meta);
	}

	private BdfMessageContext validateBackupMessage(BdfList body)
			throws FormatException {
		// Message type, version, backup payload
		checkSize(body, 3);
		int version = body.getLong(1).intValue();
		if (version < 0) throw new FormatException();
		byte[] payload = body.getRaw(2);
		BdfDictionary meta = BdfDictionary.of(
				new BdfEntry(MSG_KEY_MESSAGE_TYPE, BACKUP.getValue()),
				new BdfEntry(MSG_KEY_LOCAL, false),
				new BdfEntry(MSG_KEY_VERSION, version));
		return new BdfMessageContext(meta);
	}
}
