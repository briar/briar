package net.sf.briar.plugins.modem;

import java.io.IOException;
import java.io.OutputStream;

class SenderOutputStream extends OutputStream {

	private final Sender sender;

	private byte[] buf = null;
	private int offset = 0;
	private long sequenceNumber = 1L;

	SenderOutputStream(Sender sender) {
		this.sender = sender;
	}

	@Override
	public void close() throws IOException {
		if(buf == null) assignBuffer();
		send(true);
	}

	@Override
	public void flush() throws IOException {
		if(buf != null) send(false);
	}

	@Override
	public void write(int b) throws IOException {
		if(buf == null) assignBuffer();
		buf[offset] = (byte) b;
		offset++;
		if(offset == Data.HEADER_LENGTH + Data.MAX_PAYLOAD_LENGTH) send(false);
	}

	@Override
	public void write(byte[] b) throws IOException {
		write(b, 0, b.length);
	}

	@Override
	public void write(byte[] b, int off, int len) throws IOException {
		if(buf == null) assignBuffer();
		int available = Data.MAX_LENGTH - offset - Data.FOOTER_LENGTH;
		while(available <= len) {
			System.arraycopy(b, off, buf, offset, available);
			offset += available;
			send(false);
			assignBuffer();
			off += available;
			len -= available;
			available = Data.MAX_LENGTH - offset - Data.FOOTER_LENGTH;
		}
		System.arraycopy(b, off, buf, offset, len);
		offset += len;
	}

	private void assignBuffer() {
		buf = new byte[Data.MAX_LENGTH];
		offset = Data.HEADER_LENGTH;
	}

	private void send(boolean lastFrame) throws IOException {
		Data d = new Data(buf, offset + Data.FOOTER_LENGTH);
		d.setLastFrame(lastFrame);
		d.setSequenceNumber(sequenceNumber++);
		d.setChecksum(d.calculateChecksum());
		try {
			sender.write(d);
		} catch(InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new IOException("Interrupted while writing");
		}
		buf = null;
		offset = 0;
	}
}
