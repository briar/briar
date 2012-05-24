package net.sf.briar.transport;

import static net.sf.briar.api.transport.TransportConstants.MAC_LENGTH;

import java.io.IOException;
import java.io.OutputStream;

/** An encryption layer that performs no encryption. */
class NullOutgoingEncryptionLayer implements FrameWriter {

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

	public void writeFrame(Frame f) throws IOException {
		out.write(f.getBuffer(), 0, f.getLength() + MAC_LENGTH);
		capacity -= f.getLength();
	}

	public void flush() throws IOException {
		out.flush();
	}

	public long getRemainingCapacity() {
		return capacity;
	}
}
