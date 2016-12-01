package org.briarproject.bramble.reliability;

import org.briarproject.bramble.api.nullsafety.NotNullByDefault;

import java.io.IOException;
import java.io.OutputStream;

import javax.annotation.concurrent.NotThreadSafe;

@NotThreadSafe
@NotNullByDefault
class SenderOutputStream extends OutputStream {

	private final Sender sender;
	private final byte[] buf = new byte[Data.MAX_LENGTH];

	private int offset = Data.HEADER_LENGTH;
	private long sequenceNumber = 1;

	SenderOutputStream(Sender sender) {
		this.sender = sender;
	}

	@Override
	public void close() throws IOException {
		send(true);
		try {
			sender.flush();
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new IOException("Interrupted while closing");
		}
	}

	@Override
	public void flush() throws IOException {
		if (offset > Data.HEADER_LENGTH) send(false);
		try {
			sender.flush();
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new IOException("Interrupted while flushing");
		}
	}

	@Override
	public void write(int b) throws IOException {
		buf[offset] = (byte) b;
		offset++;
		if (offset == Data.HEADER_LENGTH + Data.MAX_PAYLOAD_LENGTH) send(false);
	}

	@Override
	public void write(byte[] b) throws IOException {
		write(b, 0, b.length);
	}

	@Override
	public void write(byte[] b, int off, int len) throws IOException {
		int available = Data.MAX_LENGTH - offset - Data.FOOTER_LENGTH;
		while (available <= len) {
			System.arraycopy(b, off, buf, offset, available);
			offset += available;
			send(false);
			off += available;
			len -= available;
			available = Data.MAX_LENGTH - offset - Data.FOOTER_LENGTH;
		}
		System.arraycopy(b, off, buf, offset, len);
		offset += len;
	}

	private void send(boolean lastFrame) throws IOException {
		byte[] frame = new byte[offset + Data.FOOTER_LENGTH];
		System.arraycopy(buf, 0, frame, 0, frame.length);
		Data d = new Data(frame);
		d.setLastFrame(lastFrame);
		d.setSequenceNumber(sequenceNumber++);
		d.setChecksum(d.calculateChecksum());
		try {
			sender.write(d);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new IOException("Interrupted while writing");
		}
		offset = Data.HEADER_LENGTH;
	}
}
