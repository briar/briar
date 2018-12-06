package org.briarproject.bramble.api.io;

import org.briarproject.bramble.api.db.DbException;
import org.briarproject.bramble.api.db.NoSuchBlockException;
import org.briarproject.bramble.api.sync.MessageId;

public interface BlockSource {

	/**
	 * Returns the number of blocks in the given message.
	 */
	int getBlockCount(MessageId m) throws DbException;

	/**
	 * Returns the given block of the given message.
	 *
	 * @throws NoSuchBlockException if 'blockNumber' is greater than or equal
	 * to the number of blocks in the message
	 */
	byte[] getBlock(MessageId m, int blockNumber) throws DbException;
}
