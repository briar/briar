package net.sf.briar.plugins.modem;

import net.sf.briar.util.ByteUtils;

abstract class Frame {

	static final byte ACK_FLAG = (byte) 128, FIN_FLAG = 64;

	protected final byte[] b;
	protected final int length;

	Frame(byte[] b, int length) {
		this.b = b;
		this.length = length;
	}

	byte[] getBuffer() {
		return b;
	}

	int getLength() {
		return length;
	}

	long getChecksum() {
		return ByteUtils.readUint32(b, length - 4);
	}

	void setChecksum(long checksum) {
		ByteUtils.writeUint32(checksum, b, length - 4);
	}

	long calculateChecksum() {
		return Crc32.crc(b, 0, length - 4);
	}

	long getSequenceNumber() {
		return ByteUtils.readUint32(b, 1);
	}

	void setSequenceNumber(long sequenceNumber) {
		ByteUtils.writeUint32(sequenceNumber, b, 1);
	}

	@Override
	public int hashCode() {
		return (int) getSequenceNumber();
	}

	@Override
	public boolean equals(Object o) {
		if(o instanceof Frame) {
			Frame f = (Frame) o;
			if(b[0] != f.b[0]) return false;
			return getSequenceNumber() == f.getSequenceNumber();
		}
		return false;
	}
}
