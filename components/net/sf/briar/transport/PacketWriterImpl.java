package net.sf.briar.transport;

import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;

import javax.crypto.Mac;

import net.sf.briar.api.transport.PacketWriter;

class PacketWriterImpl extends FilterOutputStream implements PacketWriter {

	private static final int MAX_16_BIT_UNSIGNED = 65535; // 2^16 - 1
	private static final long MAX_32_BIT_UNSIGNED = 4294967295L; // 2^32 - 1

	private final Mac mac;
	private final int transportIdentifier;
	private final long connectionNumber;

	private long packetNumber = 0L;
	private boolean betweenPackets = true;

	PacketWriterImpl(OutputStream out, Mac mac, int transportIdentifier,
			long connectionNumber) {
		super(out);
		this.mac = mac;
		if(transportIdentifier < 0) throw new IllegalArgumentException();
		if(transportIdentifier > MAX_16_BIT_UNSIGNED)
			throw new IllegalArgumentException();
		this.transportIdentifier = transportIdentifier;
		if(connectionNumber < 0L) throw new IllegalArgumentException();
		if(connectionNumber > MAX_32_BIT_UNSIGNED)
			throw new IllegalArgumentException();
		this.connectionNumber = connectionNumber;
	}

	public OutputStream getOutputStream() {
		return this;
	}

	public void nextPacket() throws IOException {
		if(!betweenPackets) writeMac();
	}

	@Override
	public void write(int b) throws IOException {
		if(betweenPackets) writeTag();
		out.write(b);
		mac.update((byte) b);
	}

	@Override
	public void write(byte[] b) throws IOException {
		if(betweenPackets) writeTag();
		out.write(b);
		mac.update(b);
	}

	@Override
	public void write(byte[] b, int len, int off) throws IOException {
		if(betweenPackets) writeTag();
		out.write(b, len, off);
		mac.update(b, len, off);
	}

	private void writeMac() throws IOException {
		out.write(mac.doFinal());
		betweenPackets = true;
	}

	private void writeTag() throws IOException {
		if(packetNumber > MAX_32_BIT_UNSIGNED)
			throw new IllegalStateException();
		byte[] tag = new byte[16];
		// Encode the transport identifier as an unsigned 16-bit integer
		writeUint16(transportIdentifier, tag, 2);
		// Encode the connection number as an unsigned 32-bit integer
		writeUint32(connectionNumber, tag, 4);
		// Encode the packet number as an unsigned 32-bit integer
		writeUint32(packetNumber, tag, 8);
		// Write the tag to the underlying output stream and the MAC
		out.write(tag);
		mac.update(tag);
		packetNumber++;
		betweenPackets = false;
	}

	private void writeUint16(int i, byte[] b, int offset) {
		assert i >= 0;
		assert i <= MAX_16_BIT_UNSIGNED;
		assert b.length >= offset + 2;
		b[offset] = (byte) (i >> 8);
		b[offset + 1] = (byte) (i & 0xFF);
	}

	private void writeUint32(long i, byte[] b, int offset) {
		assert i >= 0L;
		assert i <= MAX_32_BIT_UNSIGNED;
		assert b.length >= offset + 4;
		b[offset] = (byte) (i >> 24);
		b[offset + 1] = (byte) (i >> 16 & 0xFF);
		b[offset + 2] = (byte) (i >> 8 & 0xFF);
		b[offset + 3] = (byte) (i & 0xFF);
	}
}
