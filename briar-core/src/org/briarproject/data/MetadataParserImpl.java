package org.briarproject.data;

import org.briarproject.api.FormatException;
import org.briarproject.api.data.BdfDictionary;
import org.briarproject.api.data.BdfList;
import org.briarproject.api.data.MetadataParser;
import org.briarproject.api.db.Metadata;
import org.briarproject.util.StringUtils;

import java.io.ByteArrayInputStream;
import java.util.Map;

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

class MetadataParserImpl implements MetadataParser {

	@Override
	public BdfDictionary parse(Metadata m) throws FormatException {
		BdfDictionary dict = new BdfDictionary();
		for (Map.Entry<String, byte[]> e : m.entrySet())
			dict.put(e.getKey(), parseObject(e.getValue()));
		return dict;
	}

	private Object parseObject(byte[] b) throws FormatException {
		if (b == null) return null;
		ByteArrayInputStream in = new ByteArrayInputStream(b);
		Object o = parseObject(in);
		if (in.available() > 0) throw new FormatException();
		return o;
	}

	private Object parseObject(ByteArrayInputStream in) throws FormatException {
		switch(in.read()) {
			case NULL:
				return null;
			case TRUE:
				return Boolean.TRUE;
			case FALSE:
				return Boolean.FALSE;
			case INT_8:
				return (long) parseInt8(in);
			case INT_16:
				return (long) parseInt16(in);
			case INT_32:
				return (long) parseInt32(in);
			case INT_64:
				return parseInt64(in);
			case FLOAT_64:
				return Double.longBitsToDouble(parseInt64(in));
			case STRING_8:
				return parseString(in, parseInt8(in));
			case STRING_16:
				return parseString(in, parseInt16(in));
			case STRING_32:
				return parseString(in, parseInt32(in));
			case RAW_8:
				return parseRaw(in, parseInt8(in));
			case RAW_16:
				return parseRaw(in, parseInt16(in));
			case RAW_32:
				return parseRaw(in, parseInt32(in));
			case LIST:
				return parseList(in);
			case DICTIONARY:
				return parseDictionary(in);
			default:
				throw new FormatException();
		}
	}

	private String parseString(ByteArrayInputStream in) throws FormatException {
		switch(in.read()) {
			case STRING_8:
				return parseString(in, parseInt8(in));
			case STRING_16:
				return parseString(in, parseInt16(in));
			case STRING_32:
				return parseString(in, parseInt32(in));
			default:
				throw new FormatException();
		}
	}

	private byte parseInt8(ByteArrayInputStream in) throws FormatException {
		if (in.available() < 1) throw new FormatException();
		return (byte) in.read();
	}

	private short parseInt16(ByteArrayInputStream in) throws FormatException {
		if (in.available() < 2) throw new FormatException();
		return (short) (((in.read() & 0xFF) << 8) + (in.read() & 0xFF));
	}

	private int parseInt32(ByteArrayInputStream in) throws FormatException {
		if (in.available() < 4) throw new FormatException();
		int value = 0;
		for (int i = 0; i < 4; i++)
			value |= (in.read() & 0xFF) << (24 - i * 8);
		return value;
	}

	private long parseInt64(ByteArrayInputStream in) throws FormatException {
		if (in.available() < 8) throw new FormatException();
		long value = 0;
		for (int i = 0; i < 8; i++)
			value |= (in.read() & 0xFFL) << (56 - i * 8);
		return value;
	}

	private String parseString(ByteArrayInputStream in, int len)
			throws FormatException {
		if (len < 0) throw new FormatException();
		byte[] b = new byte[len];
		if (in.read(b, 0, len) != len) throw new FormatException();
		return StringUtils.fromUtf8(b, 0, len);
	}

	private byte[] parseRaw(ByteArrayInputStream in, int len)
			throws FormatException {
		if (len < 0) throw new FormatException();
		byte[] b = new byte[len];
		if (in.read(b, 0, len) != len) throw new FormatException();
		return b;
	}

	private BdfList parseList(ByteArrayInputStream in) throws FormatException {
		BdfList list = new BdfList();
		while (peek(in) != END) list.add(parseObject(in));
		if (in.read() != END) throw new FormatException();
		return list;
	}

	private BdfDictionary parseDictionary(ByteArrayInputStream in)
			throws FormatException {
		BdfDictionary dict = new BdfDictionary();
		while (peek(in) != END) dict.put(parseString(in), parseObject(in));
		if (in.read() != END) throw new FormatException();
		return dict;
	}

	private int peek(ByteArrayInputStream in) {
		in.mark(1);
		int next = in.read();
		in.reset();
		return next;
	}
}
