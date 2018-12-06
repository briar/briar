package org.briarproject.bramble.api.sync.tree;

import org.briarproject.bramble.api.db.DbException;
import org.briarproject.bramble.api.io.BlockSink;
import org.briarproject.bramble.api.io.HashingId;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;

import java.io.IOException;
import java.io.InputStream;

@NotNullByDefault
public interface StreamHasher {

	/**
	 * Reads the given input stream, divides the data into blocks, stores
	 * the blocks and the resulting hash tree using the given block sink and
	 * temporary ID, and returns the hash tree.
	 */
	TreeNode hash(InputStream in, BlockSink sink, HashingId h)
			throws IOException, DbException;
}
