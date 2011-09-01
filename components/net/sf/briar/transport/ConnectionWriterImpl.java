package net.sf.briar.transport;

import static net.sf.briar.api.transport.TransportConstants.MAX_FRAME_LENGTH;
import static net.sf.briar.util.ByteUtils.MAX_32_BIT_UNSIGNED;

import java.io.ByteArrayOutputStream;
import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;

import javax.crypto.Mac;

import net.sf.briar.api.transport.ConnectionWriter;
import net.sf.briar.util.ByteUtils;

/**
 * A ConnectionWriter that buffers its input and writes a frame whenever there
 * is a full-size frame to write or the flush() method is called.
 */
class ConnectionWriterImpl extends FilterOutputStream
implements ConnectionWriter {

	private final ConnectionEncrypter encrypter;
	private final Mac mac;
	private final int maxPayloadLength;
	private final ByteArrayOutputStream buf;
	private final byte[] header;

	private long frame = 0L;

	ConnectionWriterImpl(ConnectionEncrypter encrypter, Mac mac) {
		super(encrypter.getOutputStream());
		this.encrypter = encrypter;
		this.mac = mac;
		maxPayloadLength = MAX_FRAME_LENGTH - 4 - mac.getMacLength();
		buf = new ByteArrayOutputStream(maxPayloadLength);
		header = new byte[4];
	}

	public OutputStream getOutputStream() {
		return this;
	}

	@Override
	public void flush() throws IOException {
		if(buf.size() > 0) writeFrame();
		out.flush();
	}

	@Override
	public void write(int b) throws IOException {
		buf.write(b);
		if(buf.size() == maxPayloadLength) writeFrame();
	}

	@Override
	public void write(byte[] b) throws IOException {
		write(b, 0, b.length);
	}

	@Override
	public void write(byte[] b, int off, int len) throws IOException {
		int available = maxPayloadLength - buf.size();
		while(available <= len) {
			buf.write(b, off, available);
			writeFrame();
			off += available;
			len -= available;
			available = maxPayloadLength;
		}
		buf.write(b, off, len);
	}

	private void writeFrame() throws IOException {
		if(frame > MAX_32_BIT_UNSIGNED) throw new IllegalStateException();
		byte[] payload = buf.toByteArray();
		if(payload.length > maxPayloadLength) throw new IllegalStateException();
		ByteUtils.writeUint16(payload.length, header, 0);
		out.write(header);
		mac.update(header);
		out.write(payload);
		mac.update(payload);
		encrypter.writeMac(mac.doFinal());
		frame++;
		buf.reset();
	}
}
