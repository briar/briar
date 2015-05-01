package org.briarproject.serial;

import static org.briarproject.serial.ObjectTypes.END;
import static org.briarproject.serial.ObjectTypes.FLOAT_64;
import static org.briarproject.serial.ObjectTypes.INT_16;
import static org.briarproject.serial.ObjectTypes.INT_32;
import static org.briarproject.serial.ObjectTypes.INT_64;
import static org.briarproject.serial.ObjectTypes.INT_8;
import static org.briarproject.serial.ObjectTypes.LIST;
import static org.briarproject.serial.ObjectTypes.MAP;
import static org.briarproject.serial.ObjectTypes.NULL;
import static org.briarproject.serial.ObjectTypes.RAW_16;
import static org.briarproject.serial.ObjectTypes.RAW_32;
import static org.briarproject.serial.ObjectTypes.RAW_8;
import static org.briarproject.serial.ObjectTypes.STRING_16;
import static org.briarproject.serial.ObjectTypes.STRING_32;
import static org.briarproject.serial.ObjectTypes.STRING_8;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collection;

import org.briarproject.api.FormatException;
import org.briarproject.api.serial.Consumer;
import org.briarproject.api.serial.Reader;

// This class is not thread-safe
class ReaderImpl implements Reader {

	private static final byte[] EMPTY_BUFFER = new byte[] {};

	private final InputStream in;
	private final Collection<Consumer> consumers = new ArrayList<Consumer>(0);

	private boolean hasLookahead = false, eof = false;
	private byte next;
	private byte[] buf = new byte[8];

	ReaderImpl(InputStream in) {
		this.in = in;
	}

	private void readLookahead() throws IOException {
		assert !eof;
		assert !hasLookahead;
		// Read a lookahead byte
		int i = in.read();
		if(i == -1) {
			eof = true;
			return;
		}
		next = (byte) i;
		hasLookahead = true;
	}

	private void consumeLookahead() throws IOException {
		assert hasLookahead;
		for(Consumer c : consumers) c.write(next);
		hasLookahead = false;
	}

	private void readIntoBuffer(byte[] b, int length, boolean consume)
			throws IOException {
		int offset = 0;
		while(offset < length) {
			int read = in.read(b, offset, length - offset);
			if(read == -1) throw new FormatException();
			offset += read;
		}
		if(consume) for(Consumer c : consumers) c.write(b, 0, length);
	}

	private void readIntoBuffer(int length, boolean consume)
			throws IOException {
		if(buf.length < length) buf = new byte[length];
		readIntoBuffer(buf, length, consume);
	}

	private void skip(int length) throws IOException {
		while(length > 0) {
			int read = in.read(buf, 0, Math.min(length, buf.length));
			if(read == -1) throw new FormatException();
			length -= read;
		}
	}

	private void skipObject() throws IOException {
		if(hasBoolean()) skipBoolean();
		else if(hasInteger()) skipInteger();
		else if(hasFloat()) skipFloat();
		else if(hasString()) skipString();
		else if(hasBytes()) skipBytes();
		else if(hasList()) skipList();
		else if(hasMap()) skipMap();
		else if(hasNull()) skipNull();
		else throw new FormatException();
	}

	public boolean eof() throws IOException {
		if(!hasLookahead) readLookahead();
		return eof;
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

	public boolean hasNull() throws IOException {
		if(!hasLookahead) readLookahead();
		if(eof) return false;
		return next == NULL;
	}

	public void readNull() throws IOException {
		if(!hasNull()) throw new FormatException();
		consumeLookahead();
	}

	public void skipNull() throws IOException {
		if(!hasNull()) throw new FormatException();
		hasLookahead = false;
	}

	public boolean hasBoolean() throws IOException {
		if(!hasLookahead) readLookahead();
		if(eof) return false;
		return next == ObjectTypes.BOOLEAN;
	}

	public boolean readBoolean() throws IOException {
		if(!hasBoolean()) throw new FormatException();
		consumeLookahead();
		return readBoolean(true);
	}

	private boolean readBoolean(boolean consume) throws IOException {
		readIntoBuffer(1, consume);
		if(buf[0] != 0 && buf[0] != 1) throw new FormatException();
		return buf[0] == 1;
	}

	public void skipBoolean() throws IOException {
		if(!hasBoolean()) throw new FormatException();
		skip(1);
		hasLookahead = false;
	}

	public boolean hasInteger() throws IOException {
		if(!hasLookahead) readLookahead();
		if(eof) return false;
		return next == INT_8 || next == INT_16 || next == INT_32 ||
				next == INT_64;
	}

	public long readInteger() throws IOException {
		if(!hasInteger()) throw new FormatException();
		consumeLookahead();
		if(next == INT_8) return readInt8(true);
		if(next == INT_16) return readInt16(true);
		if(next == INT_32) return readInt32(true);
		return readInt64(true);
	}

	private int readInt8(boolean consume) throws IOException {
		readIntoBuffer(1, consume);
		return buf[0];
	}

	private short readInt16(boolean consume) throws IOException {
		readIntoBuffer(2, consume);
		short value = (short) (((buf[0] & 0xFF) << 8) + (buf[1] & 0xFF));
		if(value >= Byte.MIN_VALUE && value <= Byte.MAX_VALUE)
			throw new FormatException();
		return value;
	}

	private int readInt32(boolean consume) throws IOException {
		readIntoBuffer(4, consume);
		int value = 0;
		for(int i = 0; i < 4; i++) value |= (buf[i] & 0xFF) << (24 - i * 8);
		if(value >= Short.MIN_VALUE && value <= Short.MAX_VALUE)
			throw new FormatException();
		return value;
	}

	private long readInt64(boolean consume) throws IOException {
		readIntoBuffer(8, consume);
		long value = 0;
		for(int i = 0; i < 8; i++) value |= (buf[i] & 0xFFL) << (56 - i * 8);
		if(value >= Integer.MIN_VALUE && value <= Integer.MAX_VALUE)
			throw new FormatException();
		return value;
	}

	public void skipInteger() throws IOException {
		if(!hasInteger()) throw new FormatException();
		if(next == INT_8) skip(1);
		else if(next == INT_16) skip(2);
		else if(next == INT_32) skip(4);
		else skip(8);
		hasLookahead = false;
	}

	public boolean hasFloat() throws IOException {
		if(!hasLookahead) readLookahead();
		if(eof) return false;
		return next == FLOAT_64;
	}

	public double readFloat() throws IOException {
		if(!hasFloat()) throw new FormatException();
		consumeLookahead();
		readIntoBuffer(8, true);
		long value = 0;
		for(int i = 0; i < 8; i++) value |= (buf[i] & 0xFFL) << (56 - i * 8);
		return Double.longBitsToDouble(value);
	}

	public void skipFloat() throws IOException {
		if(!hasFloat()) throw new FormatException();
		skip(8);
		hasLookahead = false;
	}

	public boolean hasString() throws IOException {
		if(!hasLookahead) readLookahead();
		if(eof) return false;
		return next == STRING_8 || next == STRING_16 || next == STRING_32;
	}

	public String readString(int maxLength) throws IOException {
		if(!hasString()) throw new FormatException();
		consumeLookahead();
		int length = readStringLength(true);
		if(length < 0 || length > maxLength) throw new FormatException();
		if(length == 0) return "";
		readIntoBuffer(length, true);
		return new String(buf, 0, length, "UTF-8");
	}

	private int readStringLength(boolean consume) throws IOException {
		if(next == STRING_8) return readInt8(consume);
		if(next == STRING_16) return readInt16(consume);
		if(next == STRING_32) return readInt32(consume);
		throw new FormatException();
	}

	public void skipString() throws IOException {
		if(!hasString()) throw new FormatException();
		int length = readStringLength(false);
		if(length < 0) throw new FormatException();
		skip(length);
		hasLookahead = false;
	}

	public boolean hasBytes() throws IOException {
		if(!hasLookahead) readLookahead();
		if(eof) return false;
		return next == RAW_8 || next == RAW_16 || next == RAW_32;
	}

	public byte[] readBytes(int maxLength) throws IOException {
		if(!hasBytes()) throw new FormatException();
		consumeLookahead();
		int length = readBytesLength(true);
		if(length < 0 || length > maxLength) throw new FormatException();
		if(length == 0) return EMPTY_BUFFER;
		byte[] b = new byte[length];
		readIntoBuffer(b, length, true);
		return b;
	}

	private int readBytesLength(boolean consume) throws IOException {
		if(next == RAW_8) return readInt8(consume);
		if(next == RAW_16) return readInt16(consume);
		if(next == RAW_32) return readInt32(consume);
		throw new FormatException();
	}

	public void skipBytes() throws IOException {
		if(!hasBytes()) throw new FormatException();
		int length = readBytesLength(false);
		if(length < 0) throw new FormatException();
		skip(length);
		hasLookahead = false;
	}

	public boolean hasList() throws IOException {
		if(!hasLookahead) readLookahead();
		if(eof) return false;
		return next == LIST;
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
		return next == END;
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

	public boolean hasMap() throws IOException {
		if(!hasLookahead) readLookahead();
		if(eof) return false;
		return next == MAP;
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
}
