package org.briarproject.data;

import org.briarproject.api.FormatException;
import org.briarproject.api.data.BdfDictionary;
import org.briarproject.api.data.MetadataEncoder;
import org.briarproject.api.db.Metadata;
import org.briarproject.util.StringUtils;

import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import static org.briarproject.api.data.BdfDictionary.NULL_VALUE;
import static org.briarproject.api.db.Metadata.REMOVE;
import static org.briarproject.data.Types.DICTIONARY;
import static org.briarproject.data.Types.END;
import static org.briarproject.data.Types.FALSE;
import static org.briarproject.data.Types.FLOAT_64;
import static org.briarproject.data.Types.INT_16;
import static org.briarproject.data.Types.INT_32;
import static org.briarproject.data.Types.INT_64;
import static org.briarproject.data.Types.INT_8;
import static org.briarproject.data.Types.LIST;
import static org.briarproject.data.Types.NULL;
import static org.briarproject.data.Types.RAW_16;
import static org.briarproject.data.Types.RAW_32;
import static org.briarproject.data.Types.RAW_8;
import static org.briarproject.data.Types.STRING_16;
import static org.briarproject.data.Types.STRING_32;
import static org.briarproject.data.Types.STRING_8;
import static org.briarproject.data.Types.TRUE;

class MetadataEncoderImpl implements MetadataEncoder {

	@Override
	public Metadata encode(BdfDictionary d) throws FormatException {
		Metadata m = new Metadata();
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		for (Entry<String, Object> e : d.entrySet()) {
			if (e.getValue() == NULL_VALUE) {
				// Special case: if the value is null, the key is being removed
				m.put(e.getKey(), REMOVE);
			} else {
				encodeObject(out, e.getValue());
				m.put(e.getKey(), out.toByteArray());
				out.reset();
			}
		}
		return m;
	}

	private void encodeObject(ByteArrayOutputStream out, Object o)
			throws FormatException {
		if (o == NULL_VALUE) out.write(NULL);
		else if (o instanceof Boolean) out.write((Boolean) o ? TRUE : FALSE);
		else if (o instanceof Byte) encodeInteger(out, (Byte) o);
		else if (o instanceof Short) encodeInteger(out, (Short) o);
		else if (o instanceof Integer) encodeInteger(out, (Integer) o);
		else if (o instanceof Long) encodeInteger(out, (Long) o);
		else if (o instanceof Float) encodeFloat(out, (Float) o);
		else if (o instanceof Double) encodeFloat(out, (Double) o);
		else if (o instanceof String) encodeString(out, (String) o);
		else if (o instanceof byte[]) encodeRaw(out, (byte[]) o);
		else if (o instanceof List) encodeList(out, (List) o);
		else if (o instanceof Map) encodeDictionary(out, (Map) o);
		else throw new FormatException();
	}

	private void encodeInteger(ByteArrayOutputStream out, byte i) {
		out.write(INT_8);
		encodeInt8(out, i);
	}

	private void encodeInteger(ByteArrayOutputStream out, short i) {
		if (i >= Byte.MIN_VALUE && i <= Byte.MAX_VALUE) {
			encodeInteger(out, (byte) i);
		} else {
			out.write(INT_16);
			encodeInt16(out, i);
		}
	}

	private void encodeInteger(ByteArrayOutputStream out, int i) {
		if (i >= Short.MIN_VALUE && i <= Short.MAX_VALUE) {
			encodeInteger(out, (short) i);
		} else {
			out.write(INT_32);
			encodeInt32(out, i);
		}
	}

	private void encodeInteger(ByteArrayOutputStream out, long i) {
		if (i >= Integer.MIN_VALUE && i <= Integer.MAX_VALUE) {
			encodeInteger(out, (int) i);
		} else {
			out.write(INT_64);
			encodeInt64(out, i);
		}
	}

	private void encodeInt8(ByteArrayOutputStream out, byte i) {
		out.write(i);
	}

	private void encodeInt16(ByteArrayOutputStream out, short i) {
		out.write((byte) (i >> 8));
		out.write((byte) ((i << 8) >> 8));
	}

	private void encodeInt32(ByteArrayOutputStream out, int i) {
		out.write((byte) (i >> 24));
		out.write((byte) ((i << 8) >> 24));
		out.write((byte) ((i << 16) >> 24));
		out.write((byte) ((i << 24) >> 24));
	}

	private void encodeInt64(ByteArrayOutputStream out, long i) {
		out.write((byte) (i >> 56));
		out.write((byte) ((i << 8) >> 56));
		out.write((byte) ((i << 16) >> 56));
		out.write((byte) ((i << 24) >> 56));
		out.write((byte) ((i << 32) >> 56));
		out.write((byte) ((i << 40) >> 56));
		out.write((byte) ((i << 48) >> 56));
		out.write((byte) ((i << 56) >> 56));
	}

	private void encodeFloat(ByteArrayOutputStream out, float f) {
		encodeFloat(out, (double) f);
	}

	private void encodeFloat(ByteArrayOutputStream out, double d) {
		out.write(FLOAT_64);
		encodeInt64(out, Double.doubleToLongBits(d));
	}

	private void encodeString(ByteArrayOutputStream out, String s) {
		byte[] b = StringUtils.toUtf8(s);
		if (b.length <= Byte.MAX_VALUE) {
			out.write(STRING_8);
			encodeInt8(out, (byte) b.length);
		} else if (b.length <= Short.MAX_VALUE) {
			out.write(STRING_16);
			encodeInt16(out, (short) b.length);
		} else {
			out.write(STRING_32);
			encodeInt32(out, b.length);
		}
		out.write(b, 0, b.length);
	}

	private void encodeRaw(ByteArrayOutputStream out, byte[] b) {
		if (b.length <= Byte.MAX_VALUE) {
			out.write(RAW_8);
			encodeInt8(out, (byte) b.length);
		} else if (b.length <= Short.MAX_VALUE) {
			out.write(RAW_16);
			encodeInt16(out, (short) b.length);
		} else {
			out.write(RAW_32);
			encodeInt32(out, b.length);
		}
		out.write(b, 0, b.length);
	}

	private void encodeList(ByteArrayOutputStream out, List list)
			throws FormatException {
		out.write(LIST);
		for (Object o : list) encodeObject(out, o);
		out.write(END);
	}

	private void encodeDictionary(ByteArrayOutputStream out, Map<?, ?> map)
			throws FormatException {
		out.write(DICTIONARY);
		for (Entry<?, ?> e : map.entrySet()) {
			if (!(e.getKey() instanceof String)) throw new FormatException();
			encodeString(out, (String) e.getKey());
			encodeObject(out, e.getValue());
		}
		out.write(END);
	}
}
