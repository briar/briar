package org.briarproject.bramble.api.io;

import org.briarproject.bramble.api.db.DbException;
import org.briarproject.bramble.api.sync.MessageId;
import org.briarproject.bramble.api.sync.tree.TreeHash;

import java.util.List;

public interface BlockSink {

	/**
	 * Stores a block of the message with the given temporary ID.
	 */
	void putBlock(HashingId h, int blockNumber, byte[] data) throws DbException;

	/**
	 * Sets the hash tree path of a previously stored block.
	 */
	void setPath(HashingId h, int blockNumber, List<TreeHash> path)
			throws DbException;

	/**
	 * Sets the permanent ID of the message with the given temporary ID. The
	 * temporary ID is no longer valid once this method has been called.
	 */
	void setMessageId(HashingId h, MessageId m) throws DbException;
}
