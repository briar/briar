package net.sf.briar.serial;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collection;

import net.sf.briar.api.FormatException;
import net.sf.briar.api.serial.Consumer;
import net.sf.briar.api.serial.Reader;

// This class is not thread-safe
class ReaderImpl implements Reader {

	private static final byte[] EMPTY_BUFFER = new byte[] {};

	private final InputStream in;
	private final Collection<Consumer> consumers = new ArrayList<Consumer>(0);

	private boolean hasLookahead = false, eof = false;
	private byte next, nextStructId;
	private byte[] buf = new byte[8];

	ReaderImpl(InputStream in) {
		this.in = in;
	}

	public boolean eof() throws IOException {
		if(!hasLookahead) readLookahead();
		return eof;
	}

	private void readLookahead() throws IOException {
		assert !eof;
		// If one or two lookahead bytes have been read, feed the consumers
		if(hasLookahead) consumeLookahead();
		// Read a lookahead byte
		int i = in.read();
		if(i == -1) {
			eof = true;
			return;
		}
		next = (byte) i;
		// If necessary, read another lookahead byte
		if(next == Tag.STRUCT) {
			i = in.read();
			if(i == -1) throw new FormatException();
			nextStructId = (byte) i;
		}
		hasLookahead = true;
	}

	private void consumeLookahead() throws IOException {
		assert hasLookahead;
		for(Consumer c : consumers) {
			c.write(next);
			if(next == Tag.STRUCT) c.write(nextStructId);
		}
		hasLookahead = false;
	}

	public void close() throws IOException {
		in.close();
	}

	public void addConsumer(Consumer c) {
		consumers.add(c);
	}

	public void removeConsumer(Consumer c) {
		if(!consumers.remove(c)) throw new IllegalArgumentException();
	}

	public boolean hasBoolean() throws IOException {
		if(!hasLookahead) readLookahead();
		if(eof) return false;
		return next == Tag.FALSE || next == Tag.TRUE;
	}

	public boolean readBoolean() throws IOException {
		if(!hasBoolean()) throw new FormatException();
		consumeLookahead();
		return next == Tag.TRUE;
	}

	public void skipBoolean() throws IOException {
		if(!hasBoolean()) throw new FormatException();
		hasLookahead = false;
	}

	public boolean hasUint7() throws IOException {
		if(!hasLookahead) readLookahead();
		if(eof) return false;
		return next >= 0;
	}

	public byte readUint7() throws IOException {
		if(!hasUint7()) throw new FormatException();
		consumeLookahead();
		return next;
	}

	public void skipUint7() throws IOException {
		if(!hasUint7()) throw new FormatException();
		hasLookahead = false;
	}

	public boolean hasInt8() throws IOException {
		if(!hasLookahead) readLookahead();
		if(eof) return false;
		return next == Tag.INT8;
	}

	public byte readInt8() throws IOException {
		if(!hasInt8()) throw new FormatException();
		consumeLookahead();
		int i = in.read();
		if(i == -1) {
			eof = true;
			throw new FormatException();
		}
		byte b = (byte) i;
		// Feed the hungry mouths
		for(Consumer c : consumers) c.write(b);
		return b;
	}

	public void skipInt8() throws IOException {
		if(!hasInt8()) throw new FormatException();
		if(in.read() == -1) {
			eof = true;
			throw new FormatException();
		}
		hasLookahead = false;
	}

	public boolean hasInt16() throws IOException {
		if(!hasLookahead) readLookahead();
		if(eof) return false;
		return next == Tag.INT16;
	}

	public short readInt16() throws IOException {
		if(!hasInt16()) throw new FormatException();
		consumeLookahead();
		readIntoBuffer(2);
		return (short) (((buf[0] & 0xFF) << 8) | (buf[1] & 0xFF));
	}

	private void readIntoBuffer(int length) throws IOException {
		if(buf.length < length) buf = new byte[length];
		readIntoBuffer(buf, length);
	}

	private void readIntoBuffer(byte[] b, int length) throws IOException {
		assert !hasLookahead;
		int offset = 0;
		while(offset < length) {
			int read = in.read(b, offset, length - offset);
			if(read == -1) {
				eof = true;
				throw new FormatException();
			}
			offset += read;
		}
		// Feed the hungry mouths
		for(Consumer c : consumers) c.write(b, 0, length);
	}

	public void skipInt16() throws IOException {
		if(!hasInt16()) throw new FormatException();
		hasLookahead = false;
		skip(2);
	}

	private void skip(int length) throws IOException {
		while(length > 0) {
			int read = in.read(buf, 0, Math.min(length, buf.length));
			if(read == -1) {
				eof = true;
				throw new FormatException();
			}
			length -= read;
		}
	}

	public boolean hasInt32() throws IOException {
		if(!hasLookahead) readLookahead();
		if(eof) return false;
		return next == Tag.INT32;
	}

	public int readInt32() throws IOException {
		if(!hasInt32()) throw new FormatException();
		consumeLookahead();
		return readInt32Bits();
	}

	private int readInt32Bits() throws IOException {
		readIntoBuffer(4);
		return ((buf[0] & 0xFF) << 24) | ((buf[1] & 0xFF) << 16) |
				((buf[2] & 0xFF) << 8) | (buf[3] & 0xFF);
	}

	public void skipInt32() throws IOException {
		if(!hasInt32()) throw new FormatException();
		hasLookahead = false;
		skip(4);
	}

	public boolean hasInt64() throws IOException {
		if(!hasLookahead) readLookahead();
		if(eof) return false;
		return next == Tag.INT64;
	}

	public long readInt64() throws IOException {
		if(!hasInt64()) throw new FormatException();
		consumeLookahead();
		return readInt64Bits();
	}

	private long readInt64Bits() throws IOException {
		readIntoBuffer(8);
		return ((buf[0] & 0xFFL) << 56) | ((buf[1] & 0xFFL) << 48) |
				((buf[2] & 0xFFL) << 40) | ((buf[3] & 0xFFL) << 32) |
				((buf[4] & 0xFFL) << 24) | ((buf[5] & 0xFFL) << 16) |
				((buf[6] & 0xFFL) << 8) | (buf[7] & 0xFFL);
	}

	public void skipInt64() throws IOException {
		if(!hasInt64()) throw new FormatException();
		hasLookahead = false;
		skip(8);
	}

	public boolean hasIntAny() throws IOException {
		if(!hasLookahead) readLookahead();
		if(eof) return false;
		return next >= 0 || next == Tag.INT8 || next == Tag.INT16
				|| next == Tag.INT32 || next == Tag.INT64;
	}

	public long readIntAny() throws IOException {
		if(!hasIntAny()) throw new FormatException();
		if(next >= 0) return readUint7();
		if(next == Tag.INT8) return readInt8();
		if(next == Tag.INT16) return readInt16();
		if(next == Tag.INT32) return readInt32();
		if(next == Tag.INT64) return readInt64();
		throw new IllegalStateException();
	}

	public void skipIntAny() throws IOException {
		if(!hasIntAny()) throw new FormatException();
		if(next >= 0) skipUint7();
		else if(next == Tag.INT8) skipInt8();
		else if(next == Tag.INT16) skipInt16();
		else if(next == Tag.INT32) skipInt32();
		else if(next == Tag.INT64) skipInt64();
		else throw new IllegalStateException();
	}

	public boolean hasFloat32() throws IOException {
		if(!hasLookahead) readLookahead();
		if(eof) return false;
		return next == Tag.FLOAT32;
	}

	public float readFloat32() throws IOException {
		if(!hasFloat32()) throw new FormatException();
		consumeLookahead();
		return Float.intBitsToFloat(readInt32Bits());
	}

	public void skipFloat32() throws IOException {
		if(!hasFloat32()) throw new FormatException();
		hasLookahead = false;
		skip(4);
	}

	public boolean hasFloat64() throws IOException {
		if(!hasLookahead) readLookahead();
		if(eof) return false;
		return next == Tag.FLOAT64;
	}

	public double readFloat64() throws IOException {
		if(!hasFloat64()) throw new FormatException();
		consumeLookahead();
		return Double.longBitsToDouble(readInt64Bits());
	}

	public void skipFloat64() throws IOException {
		if(!hasFloat64()) throw new FormatException();
		hasLookahead = false;
		skip(8);
	}

	public boolean hasString() throws IOException {
		if(!hasLookahead) readLookahead();
		if(eof) return false;
		return next == Tag.STRING;
	}

	public String readString(int maxLength) throws IOException {
		if(!hasString()) throw new FormatException();
		consumeLookahead();
		int length = readLength();
		if(length > maxLength) throw new FormatException();
		if(length == 0) return "";
		readIntoBuffer(length);
		return new String(buf, 0, length, "UTF-8");
	}

	private int readLength() throws IOException {
		if(!hasLength()) throw new FormatException();
		int length;
		if(next >= 0) length = readUint7();
		else if(next == Tag.INT8) length = readInt8();
		else if(next == Tag.INT16) length = readInt16();
		else if(next == Tag.INT32) length = readInt32();
		else throw new IllegalStateException();
		if(length < 0) throw new FormatException();
		return length;
	}

	private boolean hasLength() throws IOException {
		if(!hasLookahead) readLookahead();
		if(eof) return false;
		return next >= 0 || next == Tag.INT8 || next == Tag.INT16
				|| next == Tag.INT32;
	}

	public void skipString(int maxLength) throws IOException {
		if(!hasString()) throw new FormatException();
		hasLookahead = false;
		int length = readLength();
		if(length > maxLength) throw new FormatException();
		hasLookahead = false;
		skip(length);
	}

	public boolean hasBytes() throws IOException {
		if(!hasLookahead) readLookahead();
		if(eof) return false;
		return next == Tag.BYTES;
	}

	public byte[] readBytes(int maxLength) throws IOException {
		if(!hasBytes()) throw new FormatException();
		consumeLookahead();
		int length = readLength();
		if(length > maxLength) throw new FormatException();
		if(length == 0) return EMPTY_BUFFER;
		byte[] b = new byte[length];
		readIntoBuffer(b, length);
		return b;
	}

	public void skipBytes(int maxLength) throws IOException {
		if(!hasBytes()) throw new FormatException();
		hasLookahead = false;
		int length = readLength();
		if(length > maxLength) throw new FormatException();
		hasLookahead = false;
		skip(length);
	}

	public boolean hasList() throws IOException {
		if(!hasLookahead) readLookahead();
		if(eof) return false;
		return next == Tag.LIST;
	}

	public void readListStart() throws IOException {
		if(!hasList()) throw new FormatException();
		consumeLookahead();
	}

	public boolean hasListEnd() throws IOException {
		return hasEnd();
	}

	private boolean hasEnd() throws IOException {
		if(!hasLookahead) readLookahead();
		if(eof) return false;
		return next == Tag.END;
	}

	public void readListEnd() throws IOException {
		readEnd();
	}

	private void readEnd() throws IOException {
		if(!hasEnd()) throw new FormatException();
		consumeLookahead();
	}

	public void skipList() throws IOException {
		if(!hasList()) throw new FormatException();
		hasLookahead = false;
		while(!hasListEnd()) skipObject();
		hasLookahead = false;
	}

	private void skipObject() throws IOException {
		if(hasBoolean()) skipBoolean();
		else if(hasIntAny()) skipIntAny();
		else if(hasFloat32()) skipFloat32();
		else if(hasFloat64()) skipFloat64();
		else if(hasString()) skipString(Integer.MAX_VALUE);
		else if(hasBytes()) skipBytes(Integer.MAX_VALUE);
		else if(hasList()) skipList();
		else if(hasMap()) skipMap();
		else if(hasStruct()) skipStruct();
		else if(hasNull()) skipNull();
		else throw new FormatException();
	}

	public boolean hasMap() throws IOException {
		if(!hasLookahead) readLookahead();
		if(eof) return false;
		return next == Tag.MAP;
	}

	public void readMapStart() throws IOException {
		if(!hasMap()) throw new FormatException();
		consumeLookahead();
	}

	public boolean hasMapEnd() throws IOException {
		return hasEnd();
	}

	public void readMapEnd() throws IOException {
		readEnd();
	}

	public void skipMap() throws IOException {
		if(!hasMap()) throw new FormatException();
		hasLookahead = false;
		while(!hasMapEnd()) {
			skipObject();
			skipObject();
		}
		hasLookahead = false;
	}

	public boolean hasStruct() throws IOException {
		if(!hasLookahead) readLookahead();
		if(eof) return false;
		return next == Tag.STRUCT;
	}

	public boolean hasStruct(int id) throws IOException {
		if(id < 0 || id > 255) throw new IllegalArgumentException();
		if(!hasLookahead) readLookahead();
		if(eof) return false;
		return next == Tag.STRUCT && (nextStructId & 0xFF) == id;
	}

	public void readStructStart(int id) throws IOException {
		if(!hasStruct(id)) throw new FormatException();
		consumeLookahead();
	}

	public boolean hasStructEnd() throws IOException {
		return hasEnd();
	}

	public void readStructEnd() throws IOException {
		readEnd();
	}

	public void skipStruct() throws IOException {
		if(!hasStruct()) throw new FormatException();
		hasLookahead = false;
		while(!hasStructEnd()) skipObject();
		hasLookahead = false;
	}

	public boolean hasNull() throws IOException {
		if(!hasLookahead) readLookahead();
		if(eof) return false;
		return next == Tag.NULL;
	}

	public void readNull() throws IOException {
		if(!hasNull()) throw new FormatException();
		consumeLookahead();
	}

	public void skipNull() throws IOException {
		if(!hasNull()) throw new FormatException();
		hasLookahead = false;
	}
}
