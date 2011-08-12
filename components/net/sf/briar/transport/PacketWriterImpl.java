package net.sf.briar.transport;

import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;

import javax.crypto.Mac;

import net.sf.briar.api.transport.PacketWriter;

class PacketWriterImpl extends FilterOutputStream implements PacketWriter {

	private final PacketEncrypter encrypter;
	private final Mac mac;
	private final int transportId;
	private final long connection;

	private long packet = 0L;
	private boolean betweenPackets = true;

	PacketWriterImpl(PacketEncrypter encrypter, Mac mac, int transportId,
			long connection) {
		super(encrypter.getOutputStream());
		this.encrypter = encrypter;
		this.mac = mac;
		if(transportId < 0) throw new IllegalArgumentException();
		if(transportId > Constants.MAX_16_BIT_UNSIGNED)
			throw new IllegalArgumentException();
		this.transportId = transportId;
		if(connection < 0L) throw new IllegalArgumentException();
		if(connection > Constants.MAX_32_BIT_UNSIGNED)
			throw new IllegalArgumentException();
		this.connection = connection;
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
		encrypter.finishPacket();
		betweenPackets = true;
	}

	private void writeTag() throws IOException {
		assert betweenPackets;
		if(packet > Constants.MAX_32_BIT_UNSIGNED)
			throw new IllegalStateException();
		byte[] tag = TagEncoder.encodeTag(transportId, connection,
				packet);
		// Write the tag to the encrypter and start calculating the MAC
		encrypter.writeTag(tag);
		mac.update(tag);
		packet++;
		betweenPackets = false;
	}
}
