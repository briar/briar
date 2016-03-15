package org.briarproject.data;

import org.briarproject.api.Bytes;
import org.briarproject.api.FormatException;
import org.briarproject.api.data.BdfDictionary;
import org.briarproject.api.data.BdfWriter;
import org.briarproject.api.data.BdfWriterFactory;
import org.briarproject.api.data.MetadataEncoder;
import org.briarproject.api.db.Metadata;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import javax.inject.Inject;

import static org.briarproject.api.data.BdfDictionary.NULL_VALUE;
import static org.briarproject.api.db.Metadata.REMOVE;

class MetadataEncoderImpl implements MetadataEncoder {

	private final BdfWriterFactory bdfWriterFactory;

	@Inject
	MetadataEncoderImpl(BdfWriterFactory bdfWriterFactory) {
		this.bdfWriterFactory = bdfWriterFactory;
	}

	@Override
	public Metadata encode(BdfDictionary d) throws FormatException {
		Metadata m = new Metadata();
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		BdfWriter writer = bdfWriterFactory.createWriter(out);
		try {
			for (Entry<String, Object> e : d.entrySet()) {
				if (e.getValue() == NULL_VALUE) {
					// Special case: if value is null, key is being removed
					m.put(e.getKey(), REMOVE);
				} else {
					encodeObject(writer, e.getValue());
					m.put(e.getKey(), out.toByteArray());
					out.reset();
				}
			}
		} catch (FormatException e) {
			throw e;
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
		return m;
	}

	private void encodeObject(BdfWriter writer, Object o)
			throws IOException {
		if (o instanceof Boolean) writer.writeBoolean((Boolean) o);
		else if (o instanceof Byte) writer.writeLong((Byte) o);
		else if (o instanceof Short) writer.writeLong((Short) o);
		else if (o instanceof Integer) writer.writeLong((Integer) o);
		else if (o instanceof Long) writer.writeLong((Long) o);
		else if (o instanceof Float) writer.writeDouble((Float) o);
		else if (o instanceof Double) writer.writeDouble((Double) o);
		else if (o instanceof String) writer.writeString((String) o);
		else if (o instanceof byte[]) writer.writeRaw((byte[]) o);
		else if (o instanceof Bytes) writer.writeRaw(((Bytes) o).getBytes());
		else if (o instanceof List) writer.writeList((List) o);
		else if (o instanceof Map) writer.writeDictionary((Map) o);
		else throw new FormatException();
	}
}
