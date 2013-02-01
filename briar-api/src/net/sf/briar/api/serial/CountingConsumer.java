package net.sf.briar.api.serial;

import java.io.IOException;

import net.sf.briar.api.FormatException;

/**
 * A consumer that counts the number of bytes consumed and throws a
 * FormatException if the count exceeds a given limit.
 */
public class CountingConsumer implements Consumer {

	private final long limit;
	private long count = 0;

	public CountingConsumer(long limit) {
		this.limit = limit;
	}

	public long getCount() {
		return count;
	}

	public void write(byte b) throws IOException {
		count++;
		if(count > limit) throw new FormatException();
	}

	public void write(byte[] b, int off, int len) throws IOException {
		count += len;
		if(count > limit) throw new FormatException();
	}
}
