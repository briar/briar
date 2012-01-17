package net.sf.briar.transport;

import java.io.IOException;
import java.io.OutputStream;

import net.sf.briar.api.plugins.Segment;

/** An encryption layer that performs no encryption. */
class NullOutgoingEncryptionLayer implements OutgoingEncryptionLayer {

	private final OutputStream out;

	private long capacity;

	NullOutgoingEncryptionLayer(OutputStream out) {
		this.out = out;
		capacity = Long.MAX_VALUE;
	}

	NullOutgoingEncryptionLayer(OutputStream out, long capacity) {
		this.out = out;
		this.capacity = capacity;
	}

	public void writeSegment(Segment s) throws IOException {
		out.write(s.getBuffer(), 0, s.getLength());
		capacity -= s.getLength();
	}

	public void flush() throws IOException {
		out.flush();
	}

	public long getRemainingCapacity() {
		return capacity;
	}
}
