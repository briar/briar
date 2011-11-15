package net.sf.briar.transport;

import static net.sf.briar.util.ByteUtils.MAX_32_BIT_UNSIGNED;

import java.io.IOException;

import javax.crypto.Mac;
import net.sf.briar.api.crypto.ErasableKey;

import net.sf.briar.util.ByteUtils;

/**
 * A ConnectionWriter that uses padding to hinder traffic analysis. A full-size
 * frame is written each time the writeFullFrame() method is called, with
 * padding inserted if necessary. Calls to the writer's write() methods will
 * block until there is space to buffer the data.
 */
class PaddedConnectionWriter extends ConnectionWriterImpl {

	private final byte[] padding;

	private boolean closed = false;
	private IOException exception = null;

	PaddedConnectionWriter(ConnectionEncrypter encrypter, Mac mac,
			ErasableKey macKey) {
		super(encrypter, mac, macKey);
		padding = new byte[maxPayloadLength];
	}

	@Override
	public synchronized void close() throws IOException {
		if(exception != null) throw exception;
		if(buf.size() > 0) writeFrame(false);
		out.flush();
		out.close();
		closed = true;
	}

	@Override
	public void flush() throws IOException {
		// Na na na, I can't hear you
	}

	@Override
	public synchronized void write(int b) throws IOException {
		if(exception != null) throw exception;
		if(buf.size() == maxPayloadLength) waitForSpace();
		buf.write(b);
	}

	@Override
	public void write(byte[] b) throws IOException {
		write(b, 0, b.length);
	}

	@Override
	public synchronized void write(byte[] b, int off, int len)
	throws IOException {
		if(exception != null) throw exception;
		int available = maxPayloadLength - buf.size();
		while(available < len) {
			buf.write(b, off, available);
			off += available;
			len -= available;
			waitForSpace();
			available = maxPayloadLength;
		}
		buf.write(b, off, len);
	}

	/**
	 * Attempts to write a full-size frame, inserting padding if necessary, and
	 * returns true if the frame was written. If this method returns false it
	 * should not be called again.
	 */
	synchronized boolean writeFullFrame() {
		if(closed) return false;
		try {
			writeFrame(true);
			notify();
			return true;
		} catch(IOException e) {
			exception = e;
			return false;
		}
	}

	private synchronized void writeFrame(boolean pad) throws IOException {
		if(frame > MAX_32_BIT_UNSIGNED) throw new IllegalStateException();
		byte[] payload = buf.toByteArray();
		if(payload.length > maxPayloadLength) throw new IllegalStateException();
		int paddingLength = pad ? maxPayloadLength - payload.length : 0;
		ByteUtils.writeUint16(payload.length, header, 0);
		ByteUtils.writeUint16(paddingLength, header, 2);
		out.write(header);
		mac.update(header);
		out.write(payload);
		mac.update(payload);
		out.write(padding, 0, paddingLength);
		mac.update(padding, 0, paddingLength);
		encrypter.writeMac(mac.doFinal());
		frame++;
		buf.reset();
	}

	private synchronized void waitForSpace() throws IOException {
		try {
			wait();
		} catch(InterruptedException e) {
			throw new IOException(e.getMessage());
		}
		if(exception != null) throw exception;
	}
}
