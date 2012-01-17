package net.sf.briar.transport;

import static net.sf.briar.api.transport.TransportConstants.FRAME_HEADER_LENGTH;
import static net.sf.briar.api.transport.TransportConstants.MAC_LENGTH;
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

	private final OutgoingEncryptionLayer encrypter;
	private final Mac mac;
	private final byte[] buf;

	private int length = FRAME_HEADER_LENGTH;
	private long frame = 0L;

	ConnectionWriterImpl(OutgoingEncryptionLayer encrypter, Mac mac,
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
		if(mac.getMacLength() != MAC_LENGTH)
			throw new IllegalArgumentException();
		buf = new byte[MAX_FRAME_LENGTH];
	}

	public OutputStream getOutputStream() {
		return this;
	}

	public long getRemainingCapacity() {
		long capacity = encrypter.getRemainingCapacity();
		// If there's any data buffered, subtract it and its overhead
		if(length > FRAME_HEADER_LENGTH)
			capacity -= length + MAC_LENGTH;
		// Subtract the overhead from the remaining capacity
		long frames = (long) Math.ceil((double) capacity / MAX_FRAME_LENGTH);
		int overheadPerFrame = FRAME_HEADER_LENGTH + MAC_LENGTH;
		return Math.max(0L, capacity - frames * overheadPerFrame);
	}

	@Override
	public void flush() throws IOException {
		if(length > FRAME_HEADER_LENGTH) writeFrame();
		encrypter.flush();
	}

	@Override
	public void write(int b) throws IOException {
		buf[length++] = (byte) b;
		if(length + MAC_LENGTH == MAX_FRAME_LENGTH) writeFrame();
	}

	@Override
	public void write(byte[] b) throws IOException {
		write(b, 0, b.length);
	}

	@Override
	public void write(byte[] b, int off, int len) throws IOException {
		int available = MAX_FRAME_LENGTH - length - MAC_LENGTH;
		while(available <= len) {
			System.arraycopy(b, off, buf, length, available);
			length += available;
			writeFrame();
			off += available;
			len -= available;
			available = MAX_FRAME_LENGTH - length - MAC_LENGTH;
		}
		System.arraycopy(b, off, buf, length, len);
		length += len;
	}

	private void writeFrame() throws IOException {
		if(frame > MAX_32_BIT_UNSIGNED) throw new IllegalStateException();
		int payloadLength = length - FRAME_HEADER_LENGTH;
		assert payloadLength > 0;
		HeaderEncoder.encodeHeader(buf, frame, payloadLength, 0);
		mac.update(buf, 0, length);
		try {
			mac.doFinal(buf, length);
		} catch(ShortBufferException badMac) {
			throw new RuntimeException(badMac);
		}
		encrypter.writeFrame(buf, length + MAC_LENGTH);
		length = FRAME_HEADER_LENGTH;
		frame++;
	}
}
