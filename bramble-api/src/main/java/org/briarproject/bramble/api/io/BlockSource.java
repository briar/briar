package org.briarproject.bramble.api.io;

import org.briarproject.bramble.api.db.DbException;
import org.briarproject.bramble.api.sync.MessageId;

public interface BlockSource {

	int getBlockCount(MessageId m) throws DbException;

	byte[] getBlock(MessageId m, int blockNumber) throws DbException;
}
