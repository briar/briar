package net.sf.briar.reliability;

import net.sf.briar.util.ByteUtils;

abstract class Frame {

	static final byte ACK_FLAG = (byte) 128, FIN_FLAG = 64;

	protected final byte[] buf;

	protected Frame(byte[] buf) {
		this.buf = buf;
	}

	byte[] getBuffer() {
		return buf;
	}

	int getLength() {
		return buf.length;
	}

	long getChecksum() {
		return ByteUtils.readUint32(buf, buf.length - 4);
	}

	void setChecksum(long checksum) {
		ByteUtils.writeUint32(checksum, buf, buf.length - 4);
	}

	long calculateChecksum() {
		return Crc32.crc(buf, 0, buf.length - 4);
	}

	long getSequenceNumber() {
		return ByteUtils.readUint32(buf, 1);
	}

	void setSequenceNumber(long sequenceNumber) {
		ByteUtils.writeUint32(sequenceNumber, buf, 1);
	}

	@Override
	public int hashCode() {
		long sequenceNumber = getSequenceNumber();
		return buf[0] ^ (int) (sequenceNumber ^ (sequenceNumber >>> 32));
	}

	@Override
	public boolean equals(Object o) {
		if(o instanceof Frame) {
			Frame f = (Frame) o;
			return buf[0] == f.buf[0] &&
					getSequenceNumber() == f.getSequenceNumber();
		}
		return false;
	}
}
