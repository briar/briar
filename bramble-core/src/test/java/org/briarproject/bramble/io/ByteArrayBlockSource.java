package org.briarproject.bramble.io;

import org.briarproject.bramble.api.io.BlockSource;
import org.briarproject.bramble.api.sync.MessageId;

import static java.lang.System.arraycopy;

class ByteArrayBlockSource implements BlockSource {

	private final byte[] data;
	private final int blockBytes;

	ByteArrayBlockSource(byte[] data, int blockBytes) {
		this.data = data;
		this.blockBytes = blockBytes;
	}

	@Override
	public int getBlockCount(MessageId m) {
		return (data.length + blockBytes - 1) / blockBytes;
	}

	@Override
	public byte[] getBlock(MessageId m, int blockNumber) {
		int offset = blockNumber * blockBytes;
		if (offset >= data.length) throw new IllegalArgumentException();
		int length = Math.min(blockBytes, data.length - offset);
		byte[] block = new byte[length];
		arraycopy(data, offset, block, 0, length);
		return block;
	}
}
