package org.briarproject.serial;

import static org.briarproject.serial.Tag.BYTES_16;
import static org.briarproject.serial.Tag.BYTES_32;
import static org.briarproject.serial.Tag.BYTES_8;
import static org.briarproject.serial.Tag.FALSE;
import static org.briarproject.serial.Tag.FLOAT;
import static org.briarproject.serial.Tag.INTEGER_16;
import static org.briarproject.serial.Tag.INTEGER_32;
import static org.briarproject.serial.Tag.INTEGER_64;
import static org.briarproject.serial.Tag.INTEGER_8;
import static org.briarproject.serial.Tag.STRING_16;
import static org.briarproject.serial.Tag.STRING_32;
import static org.briarproject.serial.Tag.STRING_8;
import static org.briarproject.serial.Tag.TRUE;

import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.briarproject.api.Bytes;
import org.briarproject.api.serial.Consumer;
import org.briarproject.api.serial.Writer;

// This class is not thread-safe
class WriterImpl implements Writer {

	private final OutputStream out;
	private final Collection<Consumer> consumers = new ArrayList<Consumer>(0);

	WriterImpl(OutputStream out) {
		this.out = out;
	}

	public void flush() throws IOException {
		out.flush();
	}

	public void close() throws IOException {
		out.close();
	}

	public void addConsumer(Consumer c) {
		consumers.add(c);
	}

	public void removeConsumer(Consumer c) {
		if(!consumers.remove(c)) throw new IllegalArgumentException();
	}

	public void writeBoolean(boolean b) throws IOException {
		if(b) write(TRUE);
		else write(FALSE);
	}

	public void writeInteger(long i) throws IOException {
		if(i >= Byte.MIN_VALUE && i <= Byte.MAX_VALUE) {
			write(INTEGER_8);
			write((byte) i);
		} else if(i >= Short.MIN_VALUE && i <= Short.MAX_VALUE) {
			write(INTEGER_16);
			writeInt16((short) i);
		} else if(i >= Integer.MIN_VALUE && i <= Integer.MAX_VALUE) {
			write(INTEGER_32);
			writeInt32((int) i);
		} else {
			write(INTEGER_64);
			writeInt64(i);
		}
	}

	private void writeInt16(short i) throws IOException {
		write((byte) (i >> 8));
		write((byte) ((i << 8) >> 8));
	}

	private void writeInt32(int i) throws IOException {
		write((byte) (i >> 24));
		write((byte) ((i << 8) >> 24));
		write((byte) ((i << 16) >> 24));
		write((byte) ((i << 24) >> 24));
	}

	private void writeInt64(long i) throws IOException {
		write((byte) (i >> 56));
		write((byte) ((i << 8) >> 56));
		write((byte) ((i << 16) >> 56));
		write((byte) ((i << 24) >> 56));
		write((byte) ((i << 32) >> 56));
		write((byte) ((i << 40) >> 56));
		write((byte) ((i << 48) >> 56));
		write((byte) ((i << 56) >> 56));
	}

	public void writeFloat(double d) throws IOException {
		write(FLOAT);
		writeInt64(Double.doubleToRawLongBits(d));
	}

	public void writeString(String s) throws IOException {
		byte[] b = s.getBytes("UTF-8");
		if(b.length <= Byte.MAX_VALUE) {
			write(STRING_8);
			write((byte) b.length);
		} else if(b.length <= Short.MAX_VALUE) {
			write(STRING_16);
			writeInt16((short) b.length);
		} else {
			write(STRING_32);
			writeInt32(b.length);
		}
		write(b);
	}

	public void writeBytes(byte[] b) throws IOException {
		if(b.length <= Byte.MAX_VALUE) {
			write(BYTES_8);
			write((byte) b.length);
		} else if(b.length <= Short.MAX_VALUE) {
			write(BYTES_16);
			writeInt16((short) b.length);
		} else {
			write(BYTES_32);
			writeInt32(b.length);
		}
		write(b);
	}

	public void writeList(Collection<?> c) throws IOException {
		write(Tag.LIST);
		for(Object o : c) writeObject(o);
		write(Tag.END);
	}

	private void writeObject(Object o) throws IOException {
		if(o instanceof Boolean) writeBoolean((Boolean) o);
		else if(o instanceof Byte) writeInteger((Byte) o);
		else if(o instanceof Short) writeInteger((Short) o);
		else if(o instanceof Integer) writeInteger((Integer) o);
		else if(o instanceof Long) writeInteger((Long) o);
		else if(o instanceof Float) writeFloat((Float) o);
		else if(o instanceof Double) writeFloat((Double) o);
		else if(o instanceof String) writeString((String) o);
		else if(o instanceof byte[]) writeBytes((byte[]) o);
		else if(o instanceof Bytes) writeBytes(((Bytes) o).getBytes());
		else if(o instanceof List<?>) writeList((List<?>) o);
		else if(o instanceof Map<?, ?>) writeMap((Map<?, ?>) o);
		else if(o == null) writeNull();
		else throw new IllegalStateException();
	}

	public void writeListStart() throws IOException {
		write(Tag.LIST);
	}

	public void writeListEnd() throws IOException {
		write(Tag.END);
	}

	public void writeMap(Map<?, ?> m) throws IOException {
		write(Tag.MAP);
		for(Entry<?, ?> e : m.entrySet()) {
			writeObject(e.getKey());
			writeObject(e.getValue());
		}
		write(Tag.END);
	}

	public void writeMapStart() throws IOException {
		write(Tag.MAP);
	}

	public void writeMapEnd() throws IOException {
		write(Tag.END);
	}

	public void writeStructStart(int id) throws IOException {
		if(id < 0 || id > 255) throw new IllegalArgumentException();
		write(Tag.STRUCT);
		write((byte) id);
	}

	public void writeStructEnd() throws IOException {
		write(Tag.END);
	}

	public void writeNull() throws IOException {
		write(Tag.NULL);
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
