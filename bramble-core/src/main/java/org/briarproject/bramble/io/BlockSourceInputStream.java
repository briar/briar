package org.briarproject.bramble.io;

import org.briarproject.bramble.api.db.DbException;
import org.briarproject.bramble.api.io.BlockSource;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.sync.MessageId;

import java.util.concurrent.Executor;

import javax.annotation.concurrent.ThreadSafe;

/**
 * A {@link BlockInputStream} that fetches data from a {@link BlockSource}.
 */
@ThreadSafe
@NotNullByDefault
class BlockSourceInputStream extends BlockInputStream {

	private final Executor executor;
	private final BlockSource blockSource;
	private final MessageId messageId;

	private volatile int blockCount = -1;

	BlockSourceInputStream(int minBufferBytes, Executor executor,
			BlockSource blockSource, MessageId messageId) {
		super(minBufferBytes);
		this.executor = executor;
		this.blockSource = blockSource;
		this.messageId = messageId;
	}

	@Override
	void fetchBlockAsync(int blockNumber) {
		executor.execute(() -> {
			try {
				if (blockCount == -1) {
					blockCount = blockSource.getBlockCount(messageId);
				}
				if (blockNumber > blockCount) {
					fetchFailed(blockNumber, new IllegalArgumentException());
				} else if (blockNumber == blockCount) {
					fetchSucceeded(blockNumber, new byte[0], 0); // EOF
				} else {
					byte[] block = blockSource.getBlock(messageId, blockNumber);
					fetchSucceeded(blockNumber, block, block.length);
				}
			} catch (DbException e) {
				fetchFailed(blockNumber, e);
			}
		});
	}
}
