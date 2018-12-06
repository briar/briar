package org.briarproject.bramble.io;

import org.briarproject.bramble.api.db.DatabaseExecutor;
import org.briarproject.bramble.api.io.BlockSource;
import org.briarproject.bramble.api.io.MessageInputStreamFactory;
import org.briarproject.bramble.api.sync.MessageId;

import java.io.InputStream;
import java.util.concurrent.Executor;

import javax.inject.Inject;

import static org.briarproject.bramble.api.sync.SyncConstants.MAX_BLOCK_LENGTH;

class MessageInputStreamFactoryImpl implements MessageInputStreamFactory {

	private final Executor dbExecutor;
	private final BlockSource blockSource;

	@Inject
	MessageInputStreamFactoryImpl(@DatabaseExecutor Executor dbExecutor,
			BlockSource blockSource) {
		this.dbExecutor = dbExecutor;
		this.blockSource = blockSource;
	}

	@Override
	public InputStream getMessageInputStream(MessageId m) {
		return new BlockSourceInputStream(MAX_BLOCK_LENGTH, dbExecutor,
				blockSource, m);
	}
}
