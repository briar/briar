package org.briarproject.bramble.data;

import org.briarproject.bramble.api.Bytes;
import org.briarproject.bramble.api.FormatException;
import org.briarproject.bramble.api.data.BdfWriter;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import javax.annotation.Nullable;
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
class BdfWriterImpl implements BdfWriter {

	private final OutputStream out;

	BdfWriterImpl(OutputStream out) {
		this.out = out;
	}

	@Override
	public void flush() throws IOException {
		out.flush();
	}

	@Override
	public void close() throws IOException {
		out.close();
	}

	@Override
	public void writeNull() throws IOException {
		out.write(NULL);
	}

	@Override
	public void writeBoolean(boolean b) throws IOException {
		if (b) out.write(TRUE);
		else out.write(FALSE);
	}

	@Override
	public void writeLong(long i) throws IOException {
		if (i >= Byte.MIN_VALUE && i <= Byte.MAX_VALUE) {
			out.write(INT_8);
			out.write((byte) i);
		} else if (i >= Short.MIN_VALUE && i <= Short.MAX_VALUE) {
			out.write(INT_16);
			writeInt16((short) i);
		} else if (i >= Integer.MIN_VALUE && i <= Integer.MAX_VALUE) {
			out.write(INT_32);
			writeInt32((int) i);
		} else {
			out.write(INT_64);
			writeInt64(i);
		}
	}

	private void writeInt16(short i) throws IOException {
		out.write((byte) (i >> 8));
		out.write((byte) ((i << 8) >> 8));
	}

	private void writeInt32(int i) throws IOException {
		out.write((byte) (i >> 24));
		out.write((byte) ((i << 8) >> 24));
		out.write((byte) ((i << 16) >> 24));
		out.write((byte) ((i << 24) >> 24));
	}

	private void writeInt64(long i) throws IOException {
		out.write((byte) (i >> 56));
		out.write((byte) ((i << 8) >> 56));
		out.write((byte) ((i << 16) >> 56));
		out.write((byte) ((i << 24) >> 56));
		out.write((byte) ((i << 32) >> 56));
		out.write((byte) ((i << 40) >> 56));
		out.write((byte) ((i << 48) >> 56));
		out.write((byte) ((i << 56) >> 56));
	}

	@Override
	public void writeDouble(double d) throws IOException {
		out.write(FLOAT_64);
		writeInt64(Double.doubleToRawLongBits(d));
	}

	@Override
	public void writeString(String s) throws IOException {
		byte[] b = s.getBytes("UTF-8");
		if (b.length <= Byte.MAX_VALUE) {
			out.write(STRING_8);
			out.write((byte) b.length);
		} else if (b.length <= Short.MAX_VALUE) {
			out.write(STRING_16);
			writeInt16((short) b.length);
		} else {
			out.write(STRING_32);
			writeInt32(b.length);
		}
		out.write(b);
	}

	@Override
	public void writeRaw(byte[] b) throws IOException {
		if (b.length <= Byte.MAX_VALUE) {
			out.write(RAW_8);
			out.write((byte) b.length);
		} else if (b.length <= Short.MAX_VALUE) {
			out.write(RAW_16);
			writeInt16((short) b.length);
		} else {
			out.write(RAW_32);
			writeInt32(b.length);
		}
		out.write(b);
	}

	@Override
	public void writeList(Collection<?> c) throws IOException {
		out.write(LIST);
		for (Object o : c) writeObject(o);
		out.write(END);
	}

	private void writeObject(@Nullable Object o) throws IOException {
		if (o == null || o == NULL_VALUE) writeNull();
		else if (o instanceof Boolean) writeBoolean((Boolean) o);
		else if (o instanceof Byte) writeLong((Byte) o);
		else if (o instanceof Short) writeLong((Short) o);
		else if (o instanceof Integer) writeLong((Integer) o);
		else if (o instanceof Long) writeLong((Long) o);
		else if (o instanceof Float) writeDouble((Float) o);
		else if (o instanceof Double) writeDouble((Double) o);
		else if (o instanceof String) writeString((String) o);
		else if (o instanceof byte[]) writeRaw((byte[]) o);
		else if (o instanceof Bytes) writeRaw(((Bytes) o).getBytes());
		else if (o instanceof List) writeList((List) o);
		else if (o instanceof Map) writeDictionary((Map) o);
		else throw new FormatException();
	}

	@Override
	public void writeListStart() throws IOException {
		out.write(LIST);
	}

	@Override
	public void writeListEnd() throws IOException {
		out.write(END);
	}

	@Override
	public void writeDictionary(Map<?, ?> m) throws IOException {
		out.write(DICTIONARY);
		for (Entry<?, ?> e : m.entrySet()) {
			if (!(e.getKey() instanceof String)) throw new FormatException();
			writeString((String) e.getKey());
			writeObject(e.getValue());
		}
		out.write(END);
	}

	@Override
	public void writeDictionaryStart() throws IOException {
		out.write(DICTIONARY);
	}

	@Override
	public void writeDictionaryEnd() throws IOException {
		out.write(END);
	}
}
