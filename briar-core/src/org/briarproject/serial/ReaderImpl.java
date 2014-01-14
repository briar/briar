package org.briarproject.serial;

import static org.briarproject.serial.Tag.BYTES;
import static org.briarproject.serial.Tag.END;
import static org.briarproject.serial.Tag.FALSE;
import static org.briarproject.serial.Tag.FLOAT;
import static org.briarproject.serial.Tag.INTEGER;
import static org.briarproject.serial.Tag.LIST;
import static org.briarproject.serial.Tag.MAP;
import static org.briarproject.serial.Tag.NULL;
import static org.briarproject.serial.Tag.STRING;
import static org.briarproject.serial.Tag.STRUCT;
import static org.briarproject.serial.Tag.TRUE;

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
	private byte next, nextStructId;
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
		// If necessary, read another lookahead byte
		if(next == STRUCT) {
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
			if(next == STRUCT) c.write(nextStructId);
		}
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

	private int readInt32(boolean consume) throws IOException {
		readIntoBuffer(4, consume);
		int value = 0;
		for(int i = 0; i < 4; i++) value |= (buf[i] & 0xFF) << (24 - i * 8);
		return value;
	}

	private long readInt64(boolean consume) throws IOException {
		readIntoBuffer(8, consume);
		long value = 0;
		for(int i = 0; i < 8; i++) value |= (buf[i] & 0xFFL) << (56 - i * 8);
		return value;
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
		else if(hasString()) skipString(Integer.MAX_VALUE);
		else if(hasBytes()) skipBytes(Integer.MAX_VALUE);
		else if(hasList()) skipList();
		else if(hasMap()) skipMap();
		else if(hasStruct()) skipStruct();
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

	public boolean hasBoolean() throws IOException {
		if(!hasLookahead) readLookahead();
		if(eof) return false;
		return next == FALSE || next == TRUE;
	}

	public boolean readBoolean() throws IOException {
		if(!hasBoolean()) throw new FormatException();
		consumeLookahead();
		return next == TRUE;
	}

	public void skipBoolean() throws IOException {
		if(!hasBoolean()) throw new FormatException();
		hasLookahead = false;
	}

	public boolean hasInteger() throws IOException {
		if(!hasLookahead) readLookahead();
		if(eof) return false;
		return next == INTEGER;
	}

	public long readInteger() throws IOException {
		if(!hasInteger()) throw new FormatException();
		consumeLookahead();
		return readInt64(true);
	}

	public void skipInteger() throws IOException {
		if(!hasInteger()) throw new FormatException();
		skip(8);
		hasLookahead = false;
	}

	public boolean hasFloat() throws IOException {
		if(!hasLookahead) readLookahead();
		if(eof) return false;
		return next == FLOAT;
	}

	public double readFloat() throws IOException {
		if(!hasFloat()) throw new FormatException();
		consumeLookahead();
		return Double.longBitsToDouble(readInt64(true));
	}

	public void skipFloat() throws IOException {
		if(!hasFloat()) throw new FormatException();
		skip(8);
		hasLookahead = false;
	}

	public boolean hasString() throws IOException {
		if(!hasLookahead) readLookahead();
		if(eof) return false;
		return next == STRING;
	}

	public String readString(int maxLength) throws IOException {
		if(!hasString()) throw new FormatException();
		consumeLookahead();
		int length = readInt32(true);
		if(length < 0 || length > maxLength) throw new FormatException();
		if(length == 0) return "";
		readIntoBuffer(length, true);
		return new String(buf, 0, length, "UTF-8");
	}

	public void skipString(int maxLength) throws IOException {
		if(!hasString()) throw new FormatException();
		int length = readInt32(false);
		if(length < 0 || length > maxLength) throw new FormatException();
		skip(length);
		hasLookahead = false;
	}

	public boolean hasBytes() throws IOException {
		if(!hasLookahead) readLookahead();
		if(eof) return false;
		return next == BYTES;
	}

	public byte[] readBytes(int maxLength) throws IOException {
		if(!hasBytes()) throw new FormatException();
		consumeLookahead();
		int length = readInt32(true);
		if(length < 0 || length > maxLength) throw new FormatException();
		if(length == 0) return EMPTY_BUFFER;
		byte[] b = new byte[length];
		readIntoBuffer(b, length, true);
		return b;
	}

	public void skipBytes(int maxLength) throws IOException {
		if(!hasBytes()) throw new FormatException();
		int length = readInt32(false);
		if(length < 0 || length > maxLength) throw new FormatException();
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

	public boolean hasStruct() throws IOException {
		if(!hasLookahead) readLookahead();
		if(eof) return false;
		return next == STRUCT;
	}

	public boolean hasStruct(int id) throws IOException {
		if(id < 0 || id > 255) throw new IllegalArgumentException();
		if(!hasLookahead) readLookahead();
		if(eof) return false;
		return next == STRUCT && (nextStructId & 0xFF) == id;
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
}
