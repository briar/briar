package org.briarproject.bramble.api.data;

import org.briarproject.bramble.api.Bytes;
import org.briarproject.bramble.api.FormatException;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;

import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import javax.annotation.Nullable;

import static org.briarproject.bramble.api.data.BdfDictionary.NULL_VALUE;
import static org.briarproject.bramble.util.StringUtils.toHexString;

@NotNullByDefault
public class BdfStringUtils {

	public static String toString(@Nullable Object o) throws FormatException {
		return toString(o, 0);
	}

	private static String toString(@Nullable Object o, int indent)
			throws FormatException {
		if (o == null || o == NULL_VALUE) return "null";
		if (o instanceof Boolean) return o.toString();
		if (o instanceof Number) return o.toString();
		if (o instanceof String) return "\"" + o + "\"";
		if (o instanceof Bytes)
			return "x" + toHexString(((Bytes) o).getBytes());
		if (o instanceof byte[])
			return "x" + toHexString((byte[]) o);
		if (o instanceof List) {
			List<?> list = (List) o;
			StringBuilder sb = new StringBuilder();
			sb.append("[\n");
			int i = 0, size = list.size();
			for (Object e : list) {
				indent(sb, indent + 1);
				sb.append(toString(e, indent + 1));
				if (i++ < size - 1) sb.append(',');
				sb.append('\n');
			}
			indent(sb, indent);
			sb.append(']');
			return sb.toString();
		}
		if (o instanceof Map) {
			Map<?, ?> map = (Map) o;
			StringBuilder sb = new StringBuilder();
			sb.append("{\n");
			int i = 0, size = map.size();
			for (Entry e : map.entrySet()) {
				indent(sb, indent + 1);
				sb.append(toString(e.getKey(), indent + 1));
				sb.append(": ");
				sb.append(toString(e.getValue(), indent + 1));
				if (i++ < size - 1) sb.append(',');
				sb.append('\n');
			}
			indent(sb, indent);
			sb.append('}');
			return sb.toString();
		}
		throw new FormatException();
	}

	private static void indent(StringBuilder sb, int indent) {
		for (int i = 0; i < indent; i++) sb.append('\t');
	}
}
