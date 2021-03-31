package org.briarproject.briar.api.socialbackup;

import org.briarproject.bramble.api.FormatException;
import org.briarproject.bramble.api.data.BdfList;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;

@NotNullByDefault
public interface MessageParser {

	Shard parseShardMessage(BdfList body) throws FormatException;

	BackupPayload parseBackupMessage(BdfList body) throws FormatException;

	ReturnShardPayload parseReturnShardPayload(BdfList body) throws FormatException;
}
