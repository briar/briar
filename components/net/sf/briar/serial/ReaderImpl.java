package net.sf.briar.serial;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.sf.briar.api.serial.Bytes;
import net.sf.briar.api.serial.Consumer;
import net.sf.briar.api.serial.FormatException;
import net.sf.briar.api.serial.ObjectReader;
import net.sf.briar.api.serial.Reader;
import net.sf.briar.api.serial.Tag;

class ReaderImpl implements Reader {

	private static final byte[] EMPTY_BUFFER = new byte[] {};

	private final InputStream in;

	private Consumer[] consumers = new Consumer[] {};
	private ObjectReader<?>[] objectReaders = new ObjectReader<?>[] {};
	private boolean started = false, eof = false;
	private byte next, nextNext;
	private byte[] buf = null;

	ReaderImpl(InputStream in) {
		this.in = in;
	}

	public boolean eof() throws IOException {
		if(!started) readNext(true);
		return eof;
	}

	private byte readNext(boolean eofAcceptable) throws IOException {
		assert !eof;
		if(started) {
			for(Consumer c : consumers) {
				c.write(next);
				if(next == Tag.USER) c.write(nextNext);
			}
		}
		started = true;
		readLookahead(eofAcceptable);
		return next;
	}

	private void readLookahead(boolean eofAcceptable) throws IOException {
		assert started;
		// Read the lookahead byte
		int i = in.read();
		if(i == -1) {
			if(!eofAcceptable) throw new FormatException();
			eof = true;
		}
		next = (byte) i;
		// If necessary, read another lookahead byte
		if(next == Tag.USER) {
			i = in.read();
			if(i == -1) throw new FormatException();
			nextNext = (byte) i;
		}
	}

	public void close() throws IOException {
		buf = null;
		in.close();
	}

	public void addConsumer(Consumer c) {
		Consumer[] newConsumers = new Consumer[consumers.length + 1];
		System.arraycopy(consumers, 0, newConsumers, 0, consumers.length);
		newConsumers[consumers.length] = c;
		consumers = newConsumers;
	}

	public void removeConsumer(Consumer c) {
		if(consumers.length == 0) throw new IllegalArgumentException();
		Consumer[] newConsumers = new Consumer[consumers.length - 1];
		boolean found = false;
		for(int src = 0, dest = 0; src < consumers.length; src++, dest++) {
			if(!found && consumers[src].equals(c)) {
				found = true;
				src++;
			} else newConsumers[dest] = consumers[src];
		}
		if(found) consumers = newConsumers;
		else throw new IllegalArgumentException();
	}

	public void addObjectReader(int tag, ObjectReader<?> o) {
		if(tag < 0 || tag > 255) throw new IllegalArgumentException();
		if(objectReaders.length < tag + 1) {
			ObjectReader<?>[] newObjectReaders = new ObjectReader<?>[tag + 1];
			System.arraycopy(objectReaders, 0, newObjectReaders, 0,
					objectReaders.length);
			objectReaders = newObjectReaders;
		}
		objectReaders[tag] = o;	
	}

	public void removeObjectReader(int tag) {
		if(tag < 0 || tag > objectReaders.length)
			throw new IllegalArgumentException();
		objectReaders[tag] = null;
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
		if(buf == null || buf.length < length) buf = new byte[length];
		readIntoBuffer(buf, length);
	}

	private void readIntoBuffer(byte[] b, int length) throws IOException {
		b[0] = next;
		int offset = 1;
		if(next == Tag.USER) {
			b[1] = nextNext;
			offset = 2;
		}
		while(offset < length) {
			int read = in.read(b, offset, length - offset);
			if(read == -1) {
				eof = true;
				break;
			}
			offset += read;
		}
		if(offset < length) throw new FormatException();
		// Feed the hungry mouths
		for(Consumer c : consumers) c.write(b, 0, length);
		readLookahead(true);
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

	public boolean hasString() throws IOException {
		if(!started) readNext(true);
		if(eof) return false;
		return next == Tag.STRING
		|| (next & Tag.SHORT_MASK) == Tag.SHORT_STRING;
	}

	public String readString() throws IOException {
		if(!hasString()) throw new FormatException();
		if(next == Tag.STRING) {
			readNext(false);
			return readString(readLength());
		} else {
			int length = 0xFF & next ^ Tag.SHORT_STRING;
			readNext(length == 0);
			return readString(length);
		}
	}

	private String readString(int length) throws IOException {
		assert length >= 0;
		if(length == 0) return "";
		readIntoBuffer(length);
		return new String(buf, 0, length, "UTF-8");
	}

	private int readLength() throws IOException {
		if(!hasLength()) throw new FormatException();
		if(next >= 0) return readUint7();
		if(next == Tag.INT8) return readInt8();
		if(next == Tag.INT16) return readInt16();
		if(next == Tag.INT32) return readInt32();
		throw new IllegalStateException();
	}

	private boolean hasLength() throws IOException {
		if(!started) readNext(true);
		if(eof) return false;
		return next >= 0 || next == Tag.INT8 || next == Tag.INT16
		|| next == Tag.INT32;
	}

	public boolean hasBytes() throws IOException {
		if(!started) readNext(true);
		if(eof) return false;
		return next == Tag.BYTES || (next & Tag.SHORT_MASK) == Tag.SHORT_BYTES;
	}

	public byte[] readBytes() throws IOException {
		if(!hasBytes()) throw new FormatException();
		if(next == Tag.BYTES) {
			readNext(false);
			return readBytes(readLength());
		} else {
			int length = 0xFF & next ^ Tag.SHORT_BYTES;
			readNext(length == 0);
			return readBytes(length);
		}
	}

	private byte[] readBytes(int length) throws IOException {
		assert length >= 0;
		if(length == 0) return EMPTY_BUFFER;
		byte[] b = new byte[length];
		readIntoBuffer(b, length);
		return b;
	}

	public boolean hasList() throws IOException {
		if(!started) readNext(true);
		if(eof) return false;
		return next == Tag.LIST || next == Tag.LIST_START
		|| (next & Tag.SHORT_MASK) == Tag.SHORT_LIST;
	}

	public List<Object> readList() throws IOException {
		return readList(Object.class);
	}

	public <E> List<E> readList(Class<E> e) throws IOException {
		if(!hasList()) throw new FormatException();
		if(next == Tag.LIST) {
			readNext(false);
			return readList(e, readLength());
		} else if(next == Tag.LIST_START) {
			readNext(false);
			List<E> list = new ArrayList<E>();
			while(!hasEnd()) list.add(readObject(e));
			readEnd();
			return list;
		} else {
			int length = 0xFF & next ^ Tag.SHORT_LIST;
			readNext(length == 0);
			return readList(e, length);
		}
	}

	private <E> List<E> readList(Class<E> e, int length) throws IOException {
		assert length >= 0;
		List<E> list = new ArrayList<E>();
		for(int i = 0; i < length; i++) list.add(readObject(e));
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
		if(hasUserDefined()) return readUserDefined();
		if(hasBoolean()) return Boolean.valueOf(readBoolean());
		if(hasUint7()) return Byte.valueOf(readUint7());
		if(hasInt8()) return Byte.valueOf(readInt8());
		if(hasInt16()) return Short.valueOf(readInt16());
		if(hasInt32()) return Integer.valueOf(readInt32());
		if(hasInt64()) return Long.valueOf(readInt64());
		if(hasFloat32()) return Float.valueOf(readFloat32());
		if(hasFloat64()) return Double.valueOf(readFloat64());
		if(hasString()) return readString();
		if(hasBytes()) return new Bytes(readBytes());
		if(hasList()) return readList();
		if(hasMap()) return readMap();
		if(hasNull()) {
			readNull();
			return null;
		}
		throw new FormatException();
	}

	private boolean hasUserDefined() throws IOException {
		if(!started) readNext(true);
		if(eof) return false;
		if(next == Tag.USER) return true;
		if((next & Tag.SHORT_USER_MASK) == Tag.SHORT_USER) return true;
		return false;
	}

	private Object readUserDefined() throws IOException {
		if(!hasUserDefined()) throw new FormatException();
		int tag;
		if(next == Tag.USER) tag = 0xFF & nextNext;
		else tag = 0xFF & next ^ Tag.SHORT_USER;
		return readUserDefined(tag, Object.class);
	}

	private <T> T readObject(Class<T> t) throws IOException {
		try {
			return t.cast(readObject());
		} catch(ClassCastException e) {
			throw new FormatException();
		}
	}

	public boolean hasListStart() throws IOException {
		if(!started) readNext(true);
		if(eof) return false;
		return next == Tag.LIST_START;
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
		return next == Tag.MAP || next == Tag.MAP_START
		|| (next & Tag.SHORT_MASK) == Tag.SHORT_MAP;
	}

	public Map<Object, Object> readMap() throws IOException {
		return readMap(Object.class, Object.class);
	}

	public <K, V> Map<K, V> readMap(Class<K> k, Class<V> v)	throws IOException {
		if(!hasMap()) throw new FormatException();
		if(next == Tag.MAP) {
			readNext(false);
			return readMap(k, v, readLength());
		} else if(next == Tag.MAP_START) {
			readNext(false);
			Map<K, V> m = new HashMap<K, V>();
			while(!hasEnd()) m.put(readObject(k), readObject(v));
			readEnd();
			return m;
		} else {
			int size = 0xFF & next ^ Tag.SHORT_MAP;
			readNext(size == 0);
			return readMap(k, v, size);
		}
	}

	private <K, V> Map<K, V> readMap(Class<K> k, Class<V> v, int size)
	throws IOException {
		assert size >= 0;
		Map<K, V> m = new HashMap<K, V>();
		for(int i = 0; i < size; i++) m.put(readObject(k), readObject(v));
		return m;
	}

	public boolean hasMapStart() throws IOException {
		if(!started) readNext(true);
		if(eof) return false;
		return next == Tag.MAP_START;
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

	public boolean hasUserDefined(int tag) throws IOException {
		if(tag < 0 || tag > 255) throw new IllegalArgumentException();
		if(!started) readNext(true);
		if(eof) return false;
		if(next == Tag.USER)
			return tag == (0xFF & nextNext);
		else if((next & Tag.SHORT_USER_MASK) == Tag.SHORT_USER)
			return tag == (0xFF & next ^ Tag.SHORT_USER);
		else return false;
	}

	public <T> T readUserDefined(int tag, Class<T> t) throws IOException {
		if(!hasUserDefined(tag)) throw new FormatException();
		if(tag >= objectReaders.length) throw new FormatException();
		ObjectReader<?> o = objectReaders[tag];
		if(o == null) throw new FormatException();
		try {
			return t.cast(o.readObject(this));
		} catch(ClassCastException e) {
			throw new FormatException();
		}
	}

	public void readUserDefinedTag(int tag) throws IOException {
		if(!hasUserDefined(tag)) throw new FormatException();
		readNext(false);
	}
}
