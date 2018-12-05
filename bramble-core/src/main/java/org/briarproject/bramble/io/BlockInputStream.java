package org.briarproject.bramble.io;

import org.briarproject.bramble.api.nullsafety.NotNullByDefault;

import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

import javax.annotation.Nullable;
import javax.annotation.concurrent.GuardedBy;
import javax.annotation.concurrent.ThreadSafe;

import static java.lang.System.arraycopy;
import static java.lang.Thread.currentThread;

/**
 * An {@link InputStream} that asynchronously fetches blocks of data on demand.
 */
@ThreadSafe
@NotNullByDefault
abstract class BlockInputStream extends InputStream {

	private final int minBufferBytes;
	private final BlockingQueue<Buffer> queue = new ArrayBlockingQueue<>(1);
	private final Object lock = new Object();

	@GuardedBy("lock")
	@Nullable
	private Buffer buffer = null;

	@GuardedBy("lock")
	private int offset = 0;

	@GuardedBy("lock")
	private boolean fetchingBlock = false;

	abstract void fetchBlockAsync(int blockNumber);

	BlockInputStream(int minBufferBytes) {
		this.minBufferBytes = minBufferBytes;
	}

	@Override
	public int read() throws IOException {
		synchronized (lock) {
			if (!prepareRead()) return -1;
			if (buffer == null) throw new AssertionError();
			return buffer.data[offset++] & 0xFF;
		}
	}

	@Override
	public int read(byte[] b) throws IOException {
		return read(b, 0, b.length);
	}

	@Override
	public int read(byte[] b, int off, int len) throws IOException {
		if (off < 0 || len < 0 || off + len > b.length)
			throw new IllegalArgumentException();
		synchronized (lock) {
			if (!prepareRead()) return -1;
			if (buffer == null) throw new AssertionError();
			len = Math.min(len, buffer.length - offset);
			if (len < 0) throw new AssertionError();
			arraycopy(buffer.data, offset, b, off, len);
			offset += len;
			return len;
		}
	}

	private boolean prepareRead() throws IOException {
		throwExceptionIfNecessary();
		if (isEndOfStream()) return false;
		if (shouldFetchBlock()) fetchBlockAsync();
		waitForBlock();
		if (buffer == null) throw new AssertionError();
		return offset < buffer.length;
	}

	@GuardedBy("lock")
	private void throwExceptionIfNecessary() throws IOException {
		if (buffer != null && buffer.exception != null)
			throw new IOException(buffer.exception);
	}

	@GuardedBy("lock")
	private boolean isEndOfStream() {
		return buffer != null && offset == buffer.length && !fetchingBlock;
	}

	@GuardedBy("lock")
	private boolean shouldFetchBlock() {
		if (fetchingBlock) return false;
		if (buffer == null) return true;
		if (buffer.length == 0) return false;
		return buffer.length - offset < minBufferBytes;
	}

	@GuardedBy("lock")
	private void fetchBlockAsync() {
		if (buffer == null) fetchBlockAsync(0);
		else fetchBlockAsync(buffer.blockNumber + 1);
		fetchingBlock = true;
	}

	@GuardedBy("lock")
	private void waitForBlock() throws IOException {
		if (buffer != null && offset < buffer.length) return;
		try {
			buffer = queue.take();
		} catch (InterruptedException e) {
			currentThread().interrupt();
			throw new InterruptedIOException();
		}
		fetchingBlock = false;
		offset = 0;
		throwExceptionIfNecessary();
	}

	void fetchSucceeded(int blockNumber, byte[] data, int length) {
		queue.add(new Buffer(blockNumber, data, length));
	}

	void fetchFailed(int blockNumber, Exception exception) {
		queue.add(new Buffer(blockNumber, exception));
	}

	private static class Buffer {

		private final int blockNumber;
		private final byte[] data;
		private final int length;
		@Nullable
		private final Exception exception;

		private Buffer(int blockNumber, byte[] data, int length) {
			if (length < 0 || length > data.length)
				throw new IllegalArgumentException();
			this.blockNumber = blockNumber;
			this.data = data;
			this.length = length;
			exception = null;
		}

		private Buffer(int blockNumber, Exception exception) {
			this.blockNumber = blockNumber;
			this.exception = exception;
			data = new byte[0];
			length = 0;
		}
	}
}
