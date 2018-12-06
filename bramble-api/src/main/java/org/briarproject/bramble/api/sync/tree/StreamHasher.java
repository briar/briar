package org.briarproject.bramble.api.sync.tree;

import org.briarproject.bramble.api.db.DbException;
import org.briarproject.bramble.api.io.BlockSink;
import org.briarproject.bramble.api.io.HashingId;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.sync.GroupId;
import org.briarproject.bramble.api.sync.MessageId;

import java.io.IOException;
import java.io.InputStream;

@NotNullByDefault
public interface StreamHasher {

	/**
	 * Reads the given input stream, divides the data into blocks, stores
	 * the blocks and the resulting hash tree using the given block sink and
	 * temporary ID, and returns the message ID.
	 */
	MessageId hash(InputStream in, BlockSink sink, HashingId h, GroupId g,
			long timestamp) throws IOException, DbException;
}
