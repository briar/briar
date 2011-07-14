package net.sf.briar.serial;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.sf.briar.api.serial.FormatException;
import net.sf.briar.api.serial.RawByteArray;
import net.sf.briar.api.serial.Reader;
import net.sf.briar.api.serial.Tag;

class ReaderImpl implements Reader {

	private static final int TOO_LARGE_TO_KEEP = 4096;

	private final InputStream in;
	private boolean started = false, eof = false, readLimited = false;
	private byte next;
	private long rawBytesRead = 0L, readLimit = 0L;
	private byte[] buf = null;

	ReaderImpl(InputStream in) {
		this.in = in;
	}

	public boolean eof() throws IOException {
		if(!started) readNext(true);
		return eof;
	}

	private byte readNext(boolean eofAcceptable) throws IOException {
		int i = in.read();
		if(i == -1) {
			eof = true;
			if(!eofAcceptable) throw new FormatException();
		} else rawBytesRead++;
		started = true;
		if(i > 127) i -= 256;
		next = (byte) i;
		return next;
	}

	public void setReadLimit(long limit) {
		assert limit >= 0L && limit < Long.MAX_VALUE;
		readLimited = true;
		readLimit = limit;
	}

	public void resetReadLimit() {
		readLimited = false;
		readLimit = 0L;
	}

	public long getRawBytesRead() {
		if(eof) return rawBytesRead;
		else if(started) return rawBytesRead - 1L; // Exclude lookahead byte
		else return 0L;
	}

	public void close() throws IOException {
		in.close();
	}

	public boolean hasBoolean() throws IOException {
		if(!started) readNext(true);
		if(eof) return false;
		return next == Tag.FALSE || next == Tag.TRUE;
	}

	public boolean readBoolean() throws IOException {
		if(!hasBoolean()) throw new FormatException();
		int i = next;
		readNext(true);
		return i == Tag.TRUE;
	}

	public boolean hasUint7() throws IOException {
		if(!started) readNext(true);
		if(eof) return false;
		return next >= 0;
	}

	public byte readUint7() throws IOException {
		if(!hasUint7()) throw new FormatException();
		byte b = next;
		readNext(true);
		return b;
	}

	public boolean hasInt8() throws IOException {
		if(!started) readNext(true);
		if(eof) return false;
		return next == Tag.INT8;
	}

	public byte readInt8() throws IOException {
		if(!hasInt8()) throw new FormatException();
		byte b = readNext(false);
		readNext(true);
		return b;
	}

	public boolean hasInt16() throws IOException {
		if(!started) readNext(true);
		if(eof) return false;
		return next == Tag.INT16;
	}

	public short readInt16() throws IOException {
		if(!hasInt16()) throw new FormatException();
		byte b1 = readNext(false);
		byte b2 = readNext(false);
		readNext(true);
		int i = ((b1 & 0xFF) << 8) | (b2 & 0xFF);
		return (short) i;
	}

	public boolean hasInt32() throws IOException {
		if(!started) readNext(true);
		if(eof) return false;
		return next == Tag.INT32;
	}

	public int readInt32() throws IOException {
		if(!hasInt32()) throw new FormatException();
		readNext(false);
		return readInt32Bits();
	}

	private int readInt32Bits() throws IOException {
		readIntoBuffer(4);
		return ((buf[0] & 0xFF) << 24) | ((buf[1] & 0xFF) << 16) |
		((buf[2] & 0xFF) << 8) | (buf[3] & 0xFF);
	}

	private void readIntoBuffer(int length) throws IOException {
		assert length > 0;
		if(buf == null || buf.length < length) buf = new byte[length];
		buf[0] = next;
		int offset = 1, read = 0;
		while(offset < length) {
			read = in.read(buf, offset, length - offset);
			if(read == -1) break;
			offset += read;
			rawBytesRead += read;
		}
		if(offset < length) throw new FormatException();
		readNext(true);
	}

	public boolean hasInt64() throws IOException {
		if(!started) readNext(true);
		if(eof) return false;
		return next == Tag.INT64;
	}

	public long readInt64() throws IOException {
		if(!hasInt64()) throw new FormatException();
		readNext(false);
		return readInt64Bits();
	}

	private long readInt64Bits() throws IOException {
		readIntoBuffer(8);
		return ((buf[0] & 0xFFL) << 56) | ((buf[1] & 0xFFL) << 48) |
		((buf[2] & 0xFFL) << 40) | ((buf[3] & 0xFFL) << 32) |
		((buf[4] & 0xFFL) << 24) | ((buf[5] & 0xFFL) << 16) |
		((buf[6] & 0xFFL) << 8) | (buf[7] & 0xFFL);
	}

	public boolean hasIntAny() throws IOException {
		if(!started) readNext(true);
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

	public boolean hasFloat32() throws IOException {
		if(!started) readNext(true);
		if(eof) return false;
		return next == Tag.FLOAT32;
	}

	public float readFloat32() throws IOException {
		if(!hasFloat32()) throw new FormatException();
		readNext(false);
		return Float.intBitsToFloat(readInt32Bits());
	}

	public boolean hasFloat64() throws IOException {
		if(!started) readNext(true);
		if(eof) return false;
		return next == Tag.FLOAT64;
	}

	public double readFloat64() throws IOException {
		if(!hasFloat64()) throw new FormatException();
		readNext(false);
		return Double.longBitsToDouble(readInt64Bits());
	}

	public boolean hasUtf8() throws IOException {
		if(!started) readNext(true);
		if(eof) return false;
		return next == Tag.UTF8;
	}

	public String readUtf8() throws IOException {
		if(!hasUtf8()) throw new FormatException();
		readNext(false);
		long l = readIntAny();
		if(l < 0 || l > Integer.MAX_VALUE) throw new FormatException();
		int length = (int) l;
		if(length == 0) return "";
		checkLimit(length);
		readIntoBuffer(length);
		String s = new String(buf, 0, length, "UTF-8");
		if(length >= TOO_LARGE_TO_KEEP) buf = null;
		return s;
	}

	private void checkLimit(long bytes) throws FormatException {
		if(readLimited) {
			if(bytes > readLimit) throw new FormatException();
			readLimit -= bytes;
		}
	}

	public boolean hasRaw() throws IOException {
		if(!started) readNext(true);
		if(eof) return false;
		return next == Tag.RAW;
	}

	public byte[] readRaw() throws IOException {
		if(!hasRaw()) throw new FormatException();
		readNext(false);
		long l = readIntAny();
		if(l < 0 || l > Integer.MAX_VALUE) throw new FormatException();
		int length = (int) l;
		if(length == 0) return new byte[] {};
		checkLimit(length);
		readIntoBuffer(length);
		byte[] b = buf;
		buf = null;
		return b;
	}

	public boolean hasList() throws IOException {
		if(!started) readNext(true);
		if(eof) return false;
		return next == Tag.LIST_DEF || next == Tag.LIST_INDEF;
	}

	public List<Object> readList() throws IOException {
		return readList(Object.class);
	}

	public <E> List<E> readList(Class<E> e) throws IOException {
		if(!hasList()) throw new FormatException();
		boolean definite = next == Tag.LIST_DEF;
		readNext(false);
		List<E> list = new ArrayList<E>();
		if(definite) {
			long l = readIntAny();
			if(l < 0 || l > Integer.MAX_VALUE) throw new FormatException();
			int length = (int) l;
			for(int i = 0; i < length; i++) list.add(readObject(e));
		} else {
			while(!hasEnd()) list.add(readObject(e));
			readEnd();
		}
		return list;
	}

	private boolean hasEnd() throws IOException {
		if(!started) readNext(true);
		if(eof) return false;
		return next == Tag.END;
	}

	private void readEnd() throws IOException {
		if(!started) throw new IllegalStateException();
		if(!hasEnd()) throw new FormatException();
		readNext(true);
	}

	private Object readObject() throws IOException {
		if(!started) throw new IllegalStateException();
		if(hasBoolean()) return Boolean.valueOf(readBoolean());
		if(hasUint7()) return Byte.valueOf(readUint7());
		if(hasInt8()) return Byte.valueOf(readInt8());
		if(hasInt16()) return Short.valueOf(readInt16());
		if(hasInt32()) return Integer.valueOf(readInt32());
		if(hasInt64()) return Long.valueOf(readInt64());
		if(hasFloat32()) return Float.valueOf(readFloat32());
		if(hasFloat64()) return Double.valueOf(readFloat64());
		if(hasUtf8()) return readUtf8();
		if(hasRaw()) return new RawByteArray(readRaw());
		if(hasList()) return readList();
		if(hasMap()) return readMap();
		if(hasNull()) {
			readNull();
			return null;
		}
		throw new FormatException();
	}

	@SuppressWarnings("unchecked")
	private <T> T readObject(Class<T> t) throws IOException {
		try {
			return (T) readObject();
		} catch(ClassCastException e) {
			throw new FormatException();
		}
	}

	public boolean hasListStart() throws IOException {
		if(!started) readNext(true);
		if(eof) return false;
		return next == Tag.LIST_INDEF;
	}

	public void readListStart() throws IOException {
		if(!hasListStart()) throw new FormatException();
		readNext(false);
	}

	public boolean hasListEnd() throws IOException {
		return hasEnd();
	}

	public void readListEnd() throws IOException {
		readEnd();
	}

	public boolean hasMap() throws IOException {
		if(!started) readNext(true);
		if(eof) return false;
		return next == Tag.MAP_DEF || next == Tag.MAP_INDEF;
	}

	public Map<Object, Object> readMap() throws IOException {
		return readMap(Object.class, Object.class);
	}

	public <K, V> Map<K, V> readMap(Class<K> k, Class<V> v)	throws IOException {
		if(!hasMap()) throw new FormatException();
		boolean definite = next == Tag.MAP_DEF;
		readNext(false);
		Map<K, V> m = new HashMap<K, V>();
		if(definite) {
			long l = readIntAny();
			if(l < 0 || l > Integer.MAX_VALUE) throw new FormatException();
			int length = (int) l;
			for(int i = 0; i < length; i++) m.put(readObject(k), readObject(v));
		} else {
			while(!hasEnd()) m.put(readObject(k), readObject(v));
			readEnd();
		}
		return m;
	}

	public boolean hasMapStart() throws IOException {
		if(!started) readNext(true);
		if(eof) return false;
		return next == Tag.MAP_INDEF;
	}

	public void readMapStart() throws IOException {
		if(!hasMapStart()) throw new FormatException();
		readNext(false);
	}

	public boolean hasMapEnd() throws IOException {
		return hasEnd();
	}

	public void readMapEnd() throws IOException {
		readEnd();
	}

	public boolean hasNull() throws IOException {
		if(!started) readNext(true);
		if(eof) return false;
		return next == Tag.NULL;
	}

	public void readNull() throws IOException {
		if(!hasNull()) throw new FormatException();
		readNext(true);
	}
}
