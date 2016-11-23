package org.briarproject.bramble.data;

import org.briarproject.bramble.api.FormatException;
import org.briarproject.bramble.api.data.BdfDictionary;
import org.briarproject.bramble.api.data.BdfList;
import org.briarproject.bramble.api.data.BdfReader;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;

import java.io.IOException;
import java.io.InputStream;

import javax.annotation.concurrent.NotThreadSafe;

import static org.briarproject.bramble.api.data.BdfDictionary.NULL_VALUE;
import static org.briarproject.bramble.data.Types.DICTIONARY;
import static org.briarproject.bramble.data.Types.END;
import static org.briarproject.bramble.data.Types.FALSE;
import static org.briarproject.bramble.data.Types.FLOAT_64;
import static org.briarproject.bramble.data.Types.INT_16;
import static org.briarproject.bramble.data.Types.INT_32;
import static org.briarproject.bramble.data.Types.INT_64;
import static org.briarproject.bramble.data.Types.INT_8;
import static org.briarproject.bramble.data.Types.LIST;
import static org.briarproject.bramble.data.Types.NULL;
import static org.briarproject.bramble.data.Types.RAW_16;
import static org.briarproject.bramble.data.Types.RAW_32;
import static org.briarproject.bramble.data.Types.RAW_8;
import static org.briarproject.bramble.data.Types.STRING_16;
import static org.briarproject.bramble.data.Types.STRING_32;
import static org.briarproject.bramble.data.Types.STRING_8;
import static org.briarproject.bramble.data.Types.TRUE;

@NotThreadSafe
@NotNullByDefault
class BdfReaderImpl implements BdfReader {

	private static final byte[] EMPTY_BUFFER = new byte[0];

	private final InputStream in;
	private final int nestedLimit;

	private boolean hasLookahead = false, eof = false;
	private byte next;
	private byte[] buf = new byte[8];

	BdfReaderImpl(InputStream in, int nestedLimit) {
		this.in = in;
		this.nestedLimit = nestedLimit;
	}

	private void readLookahead() throws IOException {
		if (eof) return;
		if (hasLookahead) throw new IllegalStateException();
		// Read a lookahead byte
		int i = in.read();
		if (i == -1) {
			eof = true;
			return;
		}
		next = (byte) i;
		hasLookahead = true;
	}

	private void readIntoBuffer(byte[] b, int length) throws IOException {
		int offset = 0;
		while (offset < length) {
			int read = in.read(b, offset, length - offset);
			if (read == -1) throw new FormatException();
			offset += read;
		}
	}

	private void readIntoBuffer(int length) throws IOException {
		if (buf.length < length) buf = new byte[length];
		readIntoBuffer(buf, length);
	}

	private void skip(int length) throws IOException {
		while (length > 0) {
			int read = in.read(buf, 0, Math.min(length, buf.length));
			if (read == -1) throw new FormatException();
			length -= read;
		}
	}

	private Object readObject(int level) throws IOException {
		if (hasNull()) {
			readNull();
			return NULL_VALUE;
		}
		if (hasBoolean()) return readBoolean();
		if (hasLong()) return readLong();
		if (hasDouble()) return readDouble();
		if (hasString()) return readString(Integer.MAX_VALUE);
		if (hasRaw()) return readRaw(Integer.MAX_VALUE);
		if (hasList()) return readList(level);
		if (hasDictionary()) return readDictionary(level);
		throw new FormatException();
	}

	private void skipObject() throws IOException {
		if (hasNull()) skipNull();
		else if (hasBoolean()) skipBoolean();
		else if (hasLong()) skipLong();
		else if (hasDouble()) skipDouble();
		else if (hasString()) skipString();
		else if (hasRaw()) skipRaw();
		else if (hasList()) skipList();
		else if (hasDictionary()) skipDictionary();
		else throw new FormatException();
	}

	@Override
	public boolean eof() throws IOException {
		if (!hasLookahead) readLookahead();
		return eof;
	}

	@Override
	public void close() throws IOException {
		in.close();
	}

	@Override
	public boolean hasNull() throws IOException {
		if (!hasLookahead) readLookahead();
		if (eof) return false;
		return next == NULL;
	}

	@Override
	public void readNull() throws IOException {
		if (!hasNull()) throw new FormatException();
		hasLookahead = false;
	}

	@Override
	public void skipNull() throws IOException {
		if (!hasNull()) throw new FormatException();
		hasLookahead = false;
	}

	@Override
	public boolean hasBoolean() throws IOException {
		if (!hasLookahead) readLookahead();
		if (eof) return false;
		return next == FALSE || next == TRUE;
	}

	@Override
	public boolean readBoolean() throws IOException {
		if (!hasBoolean()) throw new FormatException();
		boolean bool = next == TRUE;
		hasLookahead = false;
		return bool;
	}

	@Override
	public void skipBoolean() throws IOException {
		if (!hasBoolean()) throw new FormatException();
		hasLookahead = false;
	}

	@Override
	public boolean hasLong() throws IOException {
		if (!hasLookahead) readLookahead();
		if (eof) return false;
		return next == INT_8 || next == INT_16 || next == INT_32 ||
				next == INT_64;
	}

	@Override
	public long readLong() throws IOException {
		if (!hasLong()) throw new FormatException();
		hasLookahead = false;
		if (next == INT_8) return readInt8();
		if (next == INT_16) return readInt16();
		if (next == INT_32) return readInt32();
		return readInt64();
	}

	private int readInt8() throws IOException {
		readIntoBuffer(1);
		return buf[0];
	}

	private short readInt16() throws IOException {
		readIntoBuffer(2);
		return (short) (((buf[0] & 0xFF) << 8) + (buf[1] & 0xFF));
	}

	private int readInt32() throws IOException {
		readIntoBuffer(4);
		int value = 0;
		for (int i = 0; i < 4; i++) value |= (buf[i] & 0xFF) << (24 - i * 8);
		return value;
	}

	private long readInt64() throws IOException {
		readIntoBuffer(8);
		long value = 0;
		for (int i = 0; i < 8; i++) value |= (buf[i] & 0xFFL) << (56 - i * 8);
		return value;
	}

	@Override
	public void skipLong() throws IOException {
		if (!hasLong()) throw new FormatException();
		if (next == INT_8) skip(1);
		else if (next == INT_16) skip(2);
		else if (next == INT_32) skip(4);
		else skip(8);
		hasLookahead = false;
	}

	@Override
	public boolean hasDouble() throws IOException {
		if (!hasLookahead) readLookahead();
		if (eof) return false;
		return next == FLOAT_64;
	}

	@Override
	public double readDouble() throws IOException {
		if (!hasDouble()) throw new FormatException();
		hasLookahead = false;
		readIntoBuffer(8);
		long value = 0;
		for (int i = 0; i < 8; i++) value |= (buf[i] & 0xFFL) << (56 - i * 8);
		return Double.longBitsToDouble(value);
	}

	@Override
	public void skipDouble() throws IOException {
		if (!hasDouble()) throw new FormatException();
		skip(8);
		hasLookahead = false;
	}

	@Override
	public boolean hasString() throws IOException {
		if (!hasLookahead) readLookahead();
		if (eof) return false;
		return next == STRING_8 || next == STRING_16 || next == STRING_32;
	}

	@Override
	public String readString(int maxLength) throws IOException {
		if (!hasString()) throw new FormatException();
		hasLookahead = false;
		int length = readStringLength();
		if (length < 0 || length > maxLength) throw new FormatException();
		if (length == 0) return "";
		readIntoBuffer(length);
		return new String(buf, 0, length, "UTF-8");
	}

	private int readStringLength() throws IOException {
		if (next == STRING_8) return readInt8();
		if (next == STRING_16) return readInt16();
		if (next == STRING_32) return readInt32();
		throw new FormatException();
	}

	@Override
	public void skipString() throws IOException {
		if (!hasString()) throw new FormatException();
		int length = readStringLength();
		if (length < 0) throw new FormatException();
		skip(length);
		hasLookahead = false;
	}

	@Override
	public boolean hasRaw() throws IOException {
		if (!hasLookahead) readLookahead();
		if (eof) return false;
		return next == RAW_8 || next == RAW_16 || next == RAW_32;
	}

	@Override
	public byte[] readRaw(int maxLength) throws IOException {
		if (!hasRaw()) throw new FormatException();
		hasLookahead = false;
		int length = readRawLength();
		if (length < 0 || length > maxLength) throw new FormatException();
		if (length == 0) return EMPTY_BUFFER;
		byte[] b = new byte[length];
		readIntoBuffer(b, length);
		return b;
	}

	private int readRawLength() throws IOException {
		if (next == RAW_8) return readInt8();
		if (next == RAW_16) return readInt16();
		if (next == RAW_32) return readInt32();
		throw new FormatException();
	}

	@Override
	public void skipRaw() throws IOException {
		if (!hasRaw()) throw new FormatException();
		int length = readRawLength();
		if (length < 0) throw new FormatException();
		skip(length);
		hasLookahead = false;
	}

	@Override
	public boolean hasList() throws IOException {
		if (!hasLookahead) readLookahead();
		if (eof) return false;
		return next == LIST;
	}

	@Override
	public BdfList readList() throws IOException {
		return readList(1);
	}

	private BdfList readList(int level) throws IOException {
		if (!hasList()) throw new FormatException();
		if (level > nestedLimit) throw new FormatException();
		BdfList list = new BdfList();
		readListStart();
		while (!hasListEnd()) list.add(readObject(level + 1));
		readListEnd();
		return list;
	}

	@Override
	public void readListStart() throws IOException {
		if (!hasList()) throw new FormatException();
		hasLookahead = false;
	}

	@Override
	public boolean hasListEnd() throws IOException {
		return hasEnd();
	}

	private boolean hasEnd() throws IOException {
		if (!hasLookahead) readLookahead();
		if (eof) return false;
		return next == END;
	}

	@Override
	public void readListEnd() throws IOException {
		readEnd();
	}

	private void readEnd() throws IOException {
		if (!hasEnd()) throw new FormatException();
		hasLookahead = false;
	}

	@Override
	public void skipList() throws IOException {
		if (!hasList()) throw new FormatException();
		hasLookahead = false;
		while (!hasListEnd()) skipObject();
		hasLookahead = false;
	}

	@Override
	public boolean hasDictionary() throws IOException {
		if (!hasLookahead) readLookahead();
		if (eof) return false;
		return next == DICTIONARY;
	}

	@Override
	public BdfDictionary readDictionary() throws IOException {
		return readDictionary(1);
	}

	private BdfDictionary readDictionary(int level) throws IOException {
		if (!hasDictionary()) throw new FormatException();
		if (level > nestedLimit) throw new FormatException();
		BdfDictionary dictionary = new BdfDictionary();
		readDictionaryStart();
		while (!hasDictionaryEnd())
			dictionary.put(readString(Integer.MAX_VALUE), readObject(level + 1));
		readDictionaryEnd();
		return dictionary;
	}

	@Override
	public void readDictionaryStart() throws IOException {
		if (!hasDictionary()) throw new FormatException();
		hasLookahead = false;
	}

	@Override
	public boolean hasDictionaryEnd() throws IOException {
		return hasEnd();
	}

	@Override
	public void readDictionaryEnd() throws IOException {
		readEnd();
	}

	@Override
	public void skipDictionary() throws IOException {
		if (!hasDictionary()) throw new FormatException();
		hasLookahead = false;
		while (!hasDictionaryEnd()) {
			skipString();
			skipObject();
		}
		hasLookahead = false;
	}
}
