package net.sf.briar.serial;

import java.io.IOException;
import java.io.OutputStream;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import net.sf.briar.api.serial.Raw;
import net.sf.briar.api.serial.Tag;
import net.sf.briar.api.serial.Writer;

class WriterImpl implements Writer {

	private final OutputStream out;
	private long rawBytesWritten = 0L;

	WriterImpl(OutputStream out) {
		this.out = out;
	}

	public long getRawBytesWritten() {
		return rawBytesWritten;
	}

	public void close() throws IOException {
		out.flush();
		out.close();
	}

	public void writeBoolean(boolean b) throws IOException {
		if(b) out.write(Tag.TRUE);
		else out.write(Tag.FALSE);
		rawBytesWritten++;
	}

	public void writeUint7(byte b) throws IOException {
		if(b < 0) throw new IllegalArgumentException();
		out.write(b);
		rawBytesWritten++;
	}

	public void writeInt8(byte b) throws IOException {
		out.write(Tag.INT8);
		out.write(b);
		rawBytesWritten += 2;
	}

	public void writeInt16(short s) throws IOException {
		out.write(Tag.INT16);
		out.write((byte) (s >> 8));
		out.write((byte) ((s << 8) >> 8));
		rawBytesWritten += 3;
	}

	public void writeInt32(int i) throws IOException {
		out.write(Tag.INT32);
		writeInt32Bits(i);
		rawBytesWritten += 5;
	}

	private void writeInt32Bits(int i) throws IOException {
		out.write((byte) (i >> 24));
		out.write((byte) ((i << 8) >> 24));
		out.write((byte) ((i << 16) >> 24));
		out.write((byte) ((i << 24) >> 24));
	}

	public void writeInt64(long l) throws IOException {
		out.write(Tag.INT64);
		writeInt64Bits(l);
		rawBytesWritten += 9;
	}

	private void writeInt64Bits(long l) throws IOException {
		out.write((byte) (l >> 56));
		out.write((byte) ((l << 8) >> 56));
		out.write((byte) ((l << 16) >> 56));
		out.write((byte) ((l << 24) >> 56));
		out.write((byte) ((l << 32) >> 56));
		out.write((byte) ((l << 40) >> 56));
		out.write((byte) ((l << 48) >> 56));
		out.write((byte) ((l << 56) >> 56));
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
		out.write(Tag.FLOAT32);
		writeInt32Bits(Float.floatToRawIntBits(f));
		rawBytesWritten += 5;
	}

	public void writeFloat64(double d) throws IOException {
		out.write(Tag.FLOAT64);
		writeInt64Bits(Double.doubleToRawLongBits(d));
		rawBytesWritten += 9;
	}

	public void writeUtf8(String s) throws IOException {
		out.write(Tag.UTF8);
		byte[] b = s.getBytes("UTF-8");
		writeIntAny(b.length);
		out.write(b);
		rawBytesWritten += b.length + 1;
	}

	public void writeRaw(byte[] b) throws IOException {
		out.write(Tag.RAW);
		writeIntAny(b.length);
		out.write(b);
		rawBytesWritten += b.length + 1;
	}

	public void writeRaw(Raw r) throws IOException {
		writeRaw(r.getBytes());
	}

	public void writeList(List<?> l) throws IOException {
		out.write(Tag.LIST_DEF);
		rawBytesWritten++;
		writeIntAny(l.size());
		for(Object o : l) writeObject(o);
	}

	private void writeObject(Object o) throws IOException {
		if(o instanceof Boolean) writeBoolean((Boolean) o);
		else if(o instanceof Byte) writeIntAny((Byte) o);
		else if(o instanceof Short) writeIntAny((Short) o);
		else if(o instanceof Integer) writeIntAny((Integer) o);
		else if(o instanceof Long) writeIntAny((Long) o);
		else if(o instanceof Float) writeFloat32((Float) o);
		else if(o instanceof Double) writeFloat64((Double) o);
		else if(o instanceof String) writeUtf8((String) o);
		else if(o instanceof Raw) writeRaw((Raw) o);
		else if(o instanceof List) writeList((List<?>) o);
		else if(o instanceof Map) writeMap((Map<?, ?>) o);
		else if(o == null) writeNull();
		else throw new IllegalStateException();
	}

	public void writeListStart() throws IOException {
		out.write(Tag.LIST_INDEF);
		rawBytesWritten++;
	}

	public void writeListEnd() throws IOException {
		out.write(Tag.END);
		rawBytesWritten++;
	}

	public void writeMap(Map<?, ?> m) throws IOException {
		out.write(Tag.MAP_DEF);
		rawBytesWritten++;
		writeIntAny(m.size());
		for(Entry<?, ?> e : m.entrySet()) {
			writeObject(e.getKey());
			writeObject(e.getValue());
		}
	}

	public void writeMapStart() throws IOException {
		out.write(Tag.MAP_INDEF);
		rawBytesWritten++;
	}

	public void writeMapEnd() throws IOException {
		out.write(Tag.END);
		rawBytesWritten++;
	}

	public void writeNull() throws IOException {
		out.write(Tag.NULL);
		rawBytesWritten++;
	}
}
