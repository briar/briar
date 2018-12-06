package org.briarproject.bramble.db;

import org.briarproject.bramble.api.db.DatabaseComponent;
import org.briarproject.bramble.api.db.DbException;
import org.briarproject.bramble.api.io.BlockSource;
import org.briarproject.bramble.api.sync.MessageId;

import javax.inject.Inject;

class BlockSourceImpl implements BlockSource {

	private final DatabaseComponent db;

	@Inject
	BlockSourceImpl(DatabaseComponent db) {
		this.db = db;
	}

	@Override
	public int getBlockCount(MessageId m) throws DbException {
		return db.transactionWithResult(true, txn -> db.getBlockCount(txn, m));
	}

	@Override
	public byte[] getBlock(MessageId m, int blockNumber) throws DbException {
		return db.transactionWithResult(true, txn ->
				db.getBlock(txn, m, blockNumber));
	}
}
