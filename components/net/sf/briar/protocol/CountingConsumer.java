package net.sf.briar.protocol;

import java.io.IOException;

import net.sf.briar.api.serial.Consumer;
import net.sf.briar.api.serial.FormatException;

/**
 * A consumer that counts the number of bytes consumed and throws a
 * FormatException if the count exceeds a given limit.
 */
class CountingConsumer implements Consumer {

	private final long limit;
	private long count = 0L;

	CountingConsumer(long limit) {
		this.limit = limit;
	}

	long getCount() {
		return count;
	}

	public void write(byte b) throws IOException {
		count++;
		if(count > limit) throw new FormatException();
	}

	public void write(byte[] b) throws IOException {
		count += b.length;
		if(count > limit) throw new FormatException();
	}

	public void write(byte[] b, int off, int len) throws IOException {
		count += len;
		if(count > limit) throw new FormatException();
	}
}
