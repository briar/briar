package net.sf.briar.serial;

import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import net.sf.briar.api.Bytes;
import net.sf.briar.api.serial.Consumer;
import net.sf.briar.api.serial.Writer;

class WriterImpl implements Writer {

	private final OutputStream out;
	private final List<Consumer> consumers = new ArrayList<Consumer>(0);

	WriterImpl(OutputStream out) {
		this.out = out;
	}

	public void addConsumer(Consumer c) {
		consumers.add(c);
	}

	public void removeConsumer(Consumer c) {
		if(!consumers.remove(c)) throw new IllegalArgumentException();
	}

	public void writeBoolean(boolean b) throws IOException {
		if(b) write(Tag.TRUE);
		else write(Tag.FALSE);
	}

	public void writeUint7(byte b) throws IOException {
		if(b < 0) throw new IllegalArgumentException();
		write(b);
	}

	public void writeInt8(byte b) throws IOException {
		write(Tag.INT8);
		write(b);
	}

	public void writeInt16(short s) throws IOException {
		write(Tag.INT16);
		write((byte) (s >> 8));
		write((byte) ((s << 8) >> 8));
	}

	public void writeInt32(int i) throws IOException {
		write(Tag.INT32);
		writeInt32Bits(i);
	}

	private void writeInt32Bits(int i) throws IOException {
		write((byte) (i >> 24));
		write((byte) ((i << 8) >> 24));
		write((byte) ((i << 16) >> 24));
		write((byte) ((i << 24) >> 24));
	}

	public void writeInt64(long l) throws IOException {
		write(Tag.INT64);
		writeInt64Bits(l);
	}

	private void writeInt64Bits(long l) throws IOException {
		write((byte) (l >> 56));
		write((byte) ((l << 8) >> 56));
		write((byte) ((l << 16) >> 56));
		write((byte) ((l << 24) >> 56));
		write((byte) ((l << 32) >> 56));
		write((byte) ((l << 40) >> 56));
		write((byte) ((l << 48) >> 56));
		write((byte) ((l << 56) >> 56));
	}

	public void writeIntAny(long l) throws IOException {
		if(l >= 0 && l <= Byte.MAX_VALUE)
			writeUint7((byte) l);
		else if(l >= Byte.MIN_VALUE && l <= Byte.MAX_VALUE)
			writeInt8((byte) l);
		else if(l >= Short.MIN_VALUE && l <= Short.MAX_VALUE)
			writeInt16((short) l);
		else if(l >= Integer.MIN_VALUE && l <= Integer.MAX_VALUE)
			writeInt32((int) l);
		else writeInt64(l);
	}

	public void writeFloat32(float f) throws IOException {
		write(Tag.FLOAT32);
		writeInt32Bits(Float.floatToRawIntBits(f));
	}

	public void writeFloat64(double d) throws IOException {
		write(Tag.FLOAT64);
		writeInt64Bits(Double.doubleToRawLongBits(d));
	}

	public void writeString(String s) throws IOException {
		byte[] b = s.getBytes("UTF-8");
		if(b.length < 16) write((byte) (Tag.SHORT_STRING | b.length));
		else {
			write(Tag.STRING);
			writeLength(b.length);
		}
		write(b);
	}

	private void writeLength(int i) throws IOException {
		assert i >= 0;
		// Fun fact: it's never worth writing a length as an int8
		if(i <= Byte.MAX_VALUE) writeUint7((byte) i);
		else if(i <= Short.MAX_VALUE) writeInt16((short) i);
		else writeInt32(i);
	}

	public void writeBytes(byte[] b) throws IOException {
		if(b.length < 16) write((byte) (Tag.SHORT_BYTES | b.length));
		else {
			write(Tag.BYTES);
			writeLength(b.length);
		}
		write(b);
	}

	public void writeList(Collection<?> c) throws IOException {
		int length = c.size();
		if(length < 16) write((byte) (Tag.SHORT_LIST | length));
		else {
			write(Tag.LIST);
			writeLength(length);
		}
		for(Object o : c) writeObject(o);
	}

	private void writeObject(Object o) throws IOException {
		if(o instanceof Boolean) writeBoolean((Boolean) o);
		else if(o instanceof Byte) writeIntAny((Byte) o);
		else if(o instanceof Short) writeIntAny((Short) o);
		else if(o instanceof Integer) writeIntAny((Integer) o);
		else if(o instanceof Long) writeIntAny((Long) o);
		else if(o instanceof Float) writeFloat32((Float) o);
		else if(o instanceof Double) writeFloat64((Double) o);
		else if(o instanceof String) writeString((String) o);
		else if(o instanceof Bytes) writeBytes(((Bytes) o).getBytes());
		else if(o instanceof List<?>) writeList((List<?>) o);
		else if(o instanceof Map<?, ?>) writeMap((Map<?, ?>) o);
		else if(o == null) writeNull();
		else throw new IllegalStateException();
	}

	public void writeListStart() throws IOException {
		write(Tag.LIST_START);
	}

	public void writeListEnd() throws IOException {
		write(Tag.END);
	}

	public void writeMap(Map<?, ?> m) throws IOException {
		int length = m.size();
		if(length < 16) write((byte) (Tag.SHORT_MAP | length));
		else {
			write(Tag.MAP);
			writeLength(length);
		}
		for(Entry<?, ?> e : m.entrySet()) {
			writeObject(e.getKey());
			writeObject(e.getValue());
		}
	}

	public void writeMapStart() throws IOException {
		write(Tag.MAP_START);
	}

	public void writeMapEnd() throws IOException {
		write(Tag.END);
	}

	public void writeNull() throws IOException {
		write(Tag.NULL);
	}

	public void writeUserDefinedId(int id) throws IOException {
		if(id < 0 || id > 255) throw new IllegalArgumentException();
		if(id < 32) {
			write((byte) (Tag.SHORT_USER | id));
		} else {
			write(Tag.USER);
			write((byte) id);
		}
	}

	private void write(byte b) throws IOException {
		out.write(b);
		for(Consumer c : consumers) c.write(b);
	}

	private void write(byte[] b) throws IOException {
		out.write(b);
		for(Consumer c : consumers) c.write(b, 0, b.length);
	}
}
