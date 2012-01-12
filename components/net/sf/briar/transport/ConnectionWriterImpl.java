package net.sf.briar.transport;

import static net.sf.briar.api.transport.TransportConstants.FRAME_HEADER_LENGTH;
import static net.sf.briar.api.transport.TransportConstants.MAX_FRAME_LENGTH;
import static net.sf.briar.util.ByteUtils.MAX_32_BIT_UNSIGNED;

import java.io.IOException;
import java.io.OutputStream;
import java.security.InvalidKeyException;

import javax.crypto.Mac;
import javax.crypto.ShortBufferException;

import net.sf.briar.api.crypto.ErasableKey;
import net.sf.briar.api.transport.ConnectionWriter;

/**
 * A ConnectionWriter that buffers its input and writes a frame whenever there
 * is a full-size frame to write or the flush() method is called.
 * <p>
 * This class is not thread-safe.
 */
class ConnectionWriterImpl extends OutputStream implements ConnectionWriter {

	private final ConnectionEncrypter encrypter;
	private final Mac mac;
	private final byte[] buf;

	private int bufLength = FRAME_HEADER_LENGTH;
	private long frame = 0L;

	ConnectionWriterImpl(ConnectionEncrypter encrypter, Mac mac,
			ErasableKey macKey) {
		this.encrypter = encrypter;
		this.mac = mac;
		// Initialise the MAC
		try {
			mac.init(macKey);
		} catch(InvalidKeyException badKey) {
			throw new IllegalArgumentException(badKey);
		}
		macKey.erase();
		buf = new byte[MAX_FRAME_LENGTH];
	}

	public OutputStream getOutputStream() {
		return this;
	}

	public long getRemainingCapacity() {
		long capacity = encrypter.getRemainingCapacity();
		// If there's any data buffered, subtract it and its auth overhead
		if(bufLength > FRAME_HEADER_LENGTH)
			capacity -= bufLength + mac.getMacLength();
		// Subtract the auth overhead from the remaining capacity
		long frames = (long) Math.ceil((double) capacity / MAX_FRAME_LENGTH);
		int overheadPerFrame = FRAME_HEADER_LENGTH + mac.getMacLength();
		return Math.max(0L, capacity - frames * overheadPerFrame);
	}

	@Override
	public void flush() throws IOException {
		if(bufLength > FRAME_HEADER_LENGTH) writeFrame();
		encrypter.flush();
	}

	@Override
	public void write(int b) throws IOException {
		buf[bufLength++] = (byte) b;
		if(bufLength + mac.getMacLength() == MAX_FRAME_LENGTH) writeFrame();
	}

	@Override
	public void write(byte[] b) throws IOException {
		write(b, 0, b.length);
	}

	@Override
	public void write(byte[] b, int off, int len) throws IOException {
		int available = MAX_FRAME_LENGTH - bufLength - mac.getMacLength();
		while(available <= len) {
			System.arraycopy(b, off, buf, bufLength, available);
			bufLength += available;
			writeFrame();
			off += available;
			len -= available;
			available = MAX_FRAME_LENGTH - bufLength - mac.getMacLength();
		}
		System.arraycopy(b, off, buf, bufLength, len);
		bufLength += len;
	}

	private void writeFrame() throws IOException {
		if(frame > MAX_32_BIT_UNSIGNED) throw new IllegalStateException();
		int payloadLength = bufLength - FRAME_HEADER_LENGTH;
		assert payloadLength > 0;
		HeaderEncoder.encodeHeader(buf, frame, payloadLength, 0);
		mac.update(buf, 0, bufLength);
		try {
			mac.doFinal(buf, bufLength);
		} catch(ShortBufferException badMac) {
			throw new RuntimeException(badMac);
		}
		encrypter.writeFrame(buf, bufLength + mac.getMacLength());
		bufLength = FRAME_HEADER_LENGTH;
		frame++;
	}
}
