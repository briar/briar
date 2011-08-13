package net.sf.briar.transport;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.GeneralSecurityException;
import java.util.Arrays;

import javax.crypto.Mac;

import net.sf.briar.api.serial.FormatException;
import net.sf.briar.api.transport.PacketReader;

class PacketReaderImpl extends FilterInputStream implements PacketReader {

	private final PacketDecrypter decrypter;
	private final Mac mac;
	private final int macLength, transportId;
	private final long connection;

	private long packet = 0L;
	private boolean betweenPackets = true;

	PacketReaderImpl(PacketDecrypter decrypter, Mac mac, int transportId,
			long connection) {
		super(decrypter.getInputStream());
		this.decrypter = decrypter;
		this.mac = mac;
		macLength = mac.getMacLength();
		this.transportId = transportId;
		this.connection = connection;
	}

	public InputStream getInputStream() {
		return this;
	}

	public void finishPacket() throws IOException, GeneralSecurityException {
		if(!betweenPackets) readMac();
	}

	@Override
	public int read() throws IOException {
		if(betweenPackets) readTag();
		int i = in.read();
		if(i != -1) mac.update((byte) i);
		return i;
	}

	@Override
	public int read(byte[] b) throws IOException {
		return read(b, 0, b.length);
	}

	@Override
	public int read(byte[] b, int off, int len) throws IOException {
		if(betweenPackets) readTag();
		int i = in.read(b, off, len);
		if(i != -1) mac.update(b, off, i);
		return i;
	}

	private void readMac() throws IOException, GeneralSecurityException {
		byte[] expectedMac = mac.doFinal();
		byte[] actualMac = new byte[macLength];
		InputStream in = decrypter.getInputStream();
		int offset = 0;
		while(offset < macLength) {
			int read = in.read(actualMac, offset, actualMac.length - offset);
			if(read == -1) break;
			offset += read;
		}
		if(offset < macLength) throw new GeneralSecurityException();
		if(!Arrays.equals(expectedMac, actualMac))
			throw new GeneralSecurityException();
		betweenPackets = true;
	}

	private void readTag() throws IOException {
		assert betweenPackets;
		if(packet > Constants.MAX_32_BIT_UNSIGNED)
			throw new IllegalStateException();
		byte[] tag = decrypter.readTag();
		if(tag == null) return; // EOF
		if(!TagDecoder.decodeTag(tag, transportId, connection, packet))
			throw new FormatException();
		mac.update(tag);
		packet++;
		betweenPackets = false;
	}
}
