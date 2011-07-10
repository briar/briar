package net.sf.briar.serial;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.sf.briar.api.serial.FormatException;
import net.sf.briar.api.serial.Reader;
import net.sf.briar.api.serial.Tag;

public class ReaderImpl implements Reader {

	private static final int TOO_LARGE_TO_KEEP = 4096;

	private final InputStream in;
	private boolean started = false, eof = false;
	private byte next;
	private byte[] stringBuffer = null;

	public ReaderImpl(InputStream in) {
		this.in = in;
	}

	private byte readNext(boolean eofAcceptable) throws IOException {
		started = true;
		int i = in.read();
		if(i == -1) {
			eof = true;
			if(!eofAcceptable) throw new FormatException();
		}
		if(i > 127) i -= 256;
		next = (byte) i;
		return next;
	}

	public boolean eof() throws IOException {
		if(!started) readNext(true);
		return eof;
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
		return readInt32Bits();
	}

	private int readInt32Bits() throws IOException {
		byte b1 = readNext(false);
		byte b2 = readNext(false);
		byte b3 = readNext(false);
		byte b4 = readNext(false);
		readNext(true);
		return ((b1 & 0xFF) << 24) | ((b2 & 0xFF) << 16) |
		((b3 & 0xFF) << 8) | (b4 & 0xFF);
	}

	public boolean hasInt64() throws IOException {
		if(!started) readNext(true);
		if(eof) return false;
		return next == Tag.INT64;
	}

	public long readInt64() throws IOException {
		if(!hasInt64()) throw new FormatException();
		return readInt64Bits();
	}

	private long readInt64Bits() throws IOException {
		byte b1 = readNext(false);
		byte b2 = readNext(false);
		byte b3 = readNext(false);
		byte b4 = readNext(false);
		byte b5 = readNext(false);
		byte b6 = readNext(false);
		byte b7 = readNext(false);
		byte b8 = readNext(false);
		readNext(true);
		return ((b1 & 0xFFL) << 56) | ((b2 & 0xFFL) << 48) |
		((b3 & 0xFFL) << 40) | ((b4 & 0xFFL) << 32) |
		((b5 & 0xFFL) << 24) | ((b6 & 0xFFL) << 16) |
		((b7 & 0xFFL) << 8) | (b8 & 0xFFL);
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
		return Float.intBitsToFloat(readInt32Bits());
	}

	public boolean hasFloat64() throws IOException {
		if(!started) readNext(true);
		if(eof) return false;
		return next == Tag.FLOAT64;
	}

	public double readFloat64() throws IOException {
		if(!hasFloat64()) throw new FormatException();
		return Double.longBitsToDouble(readInt64Bits());
	}

	public boolean hasUtf8() throws IOException {
		if(!started) readNext(true);
		if(eof) return false;
		return next == Tag.UTF8;
	}

	public String readUtf8() throws IOException {
		return readUtf8(Integer.MAX_VALUE);
	}

	public String readUtf8(int maxLength) throws IOException {
		if(!hasUtf8()) throw new FormatException();
		readNext(false);
		long l = readIntAny();
		if(l < 0 || l > maxLength) throw new FormatException();
		int length = (int) l;
		if(length == 0) return "";
		if(stringBuffer == null || stringBuffer.length < length)
			stringBuffer = new byte[length];
		stringBuffer[0] = next;
		int offset = 1, read = 0;
		while(offset < length && read != -1) {
			read = in.read(stringBuffer, offset, length - offset);
			if(read != -1) offset += read;
		}
		if(offset < length) throw new FormatException();
		String s = new String(stringBuffer, 0, length, "UTF-8");
		if(length >= TOO_LARGE_TO_KEEP) stringBuffer = null;
		readNext(true);
		return s;
	}

	public boolean hasRaw() throws IOException {
		if(!started) readNext(true);
		if(eof) return false;
		return next == Tag.RAW;
	}

	public byte[] readRaw() throws IOException {
		return readRaw(Integer.MAX_VALUE);
	}

	public byte[] readRaw(int maxLength) throws IOException {
		if(!hasRaw()) throw new FormatException();
		readNext(false);
		long l = readIntAny();
		if(l < 0 || l > maxLength) throw new FormatException();
		int length = (int) l;
		if(length == 0) return new byte[] {};
		byte[] b = new byte[length];
		b[0] = next;
		int offset = 1, read = 0;
		while(offset < length && read != -1) {
			read = in.read(b, offset, length - offset);
			if(read != -1) offset += read;
		}
		if(offset < length) throw new FormatException();
		readNext(true);
		return b;
	}

	public boolean hasList(boolean definite) throws IOException {
		if(!started) readNext(true);
		if(eof) return false;
		if(definite) return next == Tag.LIST_DEF;
		else return next == Tag.LIST_INDEF;
	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	public List<?> readList(boolean definite) throws IOException {
		if(!hasList(definite)) throw new FormatException();
		readNext(false);
		List list = new ArrayList();
		if(definite) {
			long l = readIntAny();
			if(l < 0 || l > Integer.MAX_VALUE) throw new FormatException();
			int length = (int) l;
			for(int i = 0; i < length; i++) list.add(readObject());
		} else {
			while(!hasEnd()) list.add(readObject());
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
		if(hasRaw()) return new RawImpl(readRaw());
		if(hasList()) return readList();
		if(hasMap()) return readMap();
		if(hasNull()) {
			readNull();
			return null;
		}
		throw new IllegalStateException();
	}

	public boolean hasList() throws IOException {
		if(!started) readNext(true);
		if(eof) return false;
		return next == Tag.LIST_DEF || next == Tag.LIST_INDEF;
	}

	public List<?> readList() throws IOException {
		if(hasList(true)) return readList(true);
		if(hasList(false)) return readList(false);
		throw new FormatException();
	}

	public boolean hasMap(boolean definite) throws IOException {
		if(!started) readNext(true);
		if(eof) return false;
		if(definite) return next == Tag.MAP_DEF;
		else return next == Tag.MAP_INDEF;
	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	public Map<?, ?> readMap(boolean definite) throws IOException {
		if(!hasMap(definite)) throw new FormatException();
		readNext(false);
		Map m = new HashMap();
		if(definite) {
			long l = readIntAny();
			if(l < 0 || l > Integer.MAX_VALUE) throw new FormatException();
			int length = (int) l;
			for(int i = 0; i < length; i++) m.put(readObject(), readObject());
		} else {
			while(!hasEnd()) m.put(readObject(), readObject());
			readEnd();
		}
		return m;
	}

	public boolean hasMap() throws IOException {
		if(!started) readNext(true);
		if(eof) return false;
		return next == Tag.MAP_DEF || next == Tag.MAP_INDEF;
	}

	public Map<?, ?> readMap() throws IOException {
		if(hasMap(true)) return readMap(true);
		if(hasMap(false)) return readMap(false);
		throw new FormatException();
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
