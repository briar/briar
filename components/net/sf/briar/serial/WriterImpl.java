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
	private long bytesWritten = 0L;

	WriterImpl(OutputStream out) {
		this.out = out;
	}

	public long getBytesWritten() {
		return bytesWritten;
	}

	public void close() throws IOException {
		out.flush();
		out.close();
	}

	public void writeBoolean(boolean b) throws IOException {
		if(b) out.write(Tag.TRUE);
		else out.write(Tag.FALSE);
		bytesWritten++;
	}

	public void writeUint7(byte b) throws IOException {
		if(b < 0) throw new IllegalArgumentException();
		out.write(b);
		bytesWritten++;
	}

	public void writeInt8(byte b) throws IOException {
		out.write(Tag.INT8);
		out.write(b);
		bytesWritten += 2;
	}

	public void writeInt16(short s) throws IOException {
		out.write(Tag.INT16);
		out.write((byte) (s >> 8));
		out.write((byte) ((s << 8) >> 8));
		bytesWritten += 3;
	}

	public void writeInt32(int i) throws IOException {
		out.write(Tag.INT32);
		writeInt32Bits(i);
		bytesWritten += 5;
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
		bytesWritten += 9;
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
		bytesWritten += 5;
	}

	public void writeFloat64(double d) throws IOException {
		out.write(Tag.FLOAT64);
		writeInt64Bits(Double.doubleToRawLongBits(d));
		bytesWritten += 9;
	}

	public void writeString(String s) throws IOException {
		byte[] b = s.getBytes("UTF-8");
		if(b.length < 16) out.write(intToByte(Tag.SHORT_STRING | b.length));
		else {
			out.write(Tag.STRING);
			writeLength(b.length);
		}
		out.write(b);
		bytesWritten += b.length + 1;
	}

	private byte intToByte(int i) {
		assert i >= 0;
		assert i <= 255;
		return (byte) (i > 127 ? i - 256 : i);
	}

	private void writeLength(int i) throws IOException {
		assert i >= 0;
		// Fun fact: it's never worth writing a length as an int8
		if(i <= Byte.MAX_VALUE) writeUint7((byte) i);
		else if(i <= Short.MAX_VALUE) writeInt16((short) i);
		else writeInt32(i);
	}

	public void writeRaw(byte[] b) throws IOException {
		if(b.length < 16) out.write(intToByte(Tag.SHORT_RAW | b.length));
		else {
			out.write(Tag.RAW);
			writeLength(b.length);
		}
		out.write(b);
		bytesWritten += b.length + 1;
	}

	public void writeRaw(Raw r) throws IOException {
		writeRaw(r.getBytes());
	}

	public void writeList(List<?> l) throws IOException {
		int length = l.size();
		if(length < 16) out.write(intToByte(Tag.SHORT_LIST | length));
		else {
			out.write(Tag.LIST);
			writeLength(length);
		}
		for(Object o : l) writeObject(o);
		bytesWritten++;
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
		else if(o instanceof Raw) writeRaw((Raw) o);
		else if(o instanceof List) writeList((List<?>) o);
		else if(o instanceof Map) writeMap((Map<?, ?>) o);
		else if(o == null) writeNull();
		else throw new IllegalStateException();
	}

	public void writeListStart() throws IOException {
		out.write(Tag.LIST_START);
		bytesWritten++;
	}

	public void writeListEnd() throws IOException {
		out.write(Tag.END);
		bytesWritten++;
	}

	public void writeMap(Map<?, ?> m) throws IOException {
		int length = m.size();
		if(length < 16) out.write(intToByte(Tag.SHORT_MAP | length));
		else {
			out.write(Tag.MAP);
			writeLength(length);
		}
		for(Entry<?, ?> e : m.entrySet()) {
			writeObject(e.getKey());
			writeObject(e.getValue());
		}
		bytesWritten++;
	}

	public void writeMapStart() throws IOException {
		out.write(Tag.MAP_START);
		bytesWritten++;
	}

	public void writeMapEnd() throws IOException {
		out.write(Tag.END);
		bytesWritten++;
	}

	public void writeNull() throws IOException {
		out.write(Tag.NULL);
		bytesWritten++;
	}

	public void writeUserDefinedTag(int tag) throws IOException {
		if(tag < 0) throw new IllegalArgumentException();
		if(tag < 32) out.write((byte) (Tag.SHORT_USER | tag));
		else {
			out.write(Tag.USER);
			writeLength(tag);
		}
		bytesWritten++;
	}
}
