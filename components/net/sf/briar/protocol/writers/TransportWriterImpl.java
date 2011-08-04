package net.sf.briar.protocol.writers;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Map;
import java.util.Map.Entry;

import net.sf.briar.api.protocol.Tags;
import net.sf.briar.api.protocol.writers.TransportWriter;
import net.sf.briar.api.serial.Writer;
import net.sf.briar.api.serial.WriterFactory;

class TransportWriterImpl implements TransportWriter {

	private final OutputStream out;
	private final Writer w;

	TransportWriterImpl(OutputStream out, WriterFactory writerFactory) {
		this.out = out;
		w = writerFactory.createWriter(out);
	}

	public void writeTransports(Map<String, Map<String, String>> transports)
	throws IOException {
		w.writeUserDefinedTag(Tags.TRANSPORTS);
		// Transport maps are always written in delimited form
		w.writeMapStart();
		for(Entry<String, Map<String, String>> e : transports.entrySet()) {
			w.writeString(e.getKey());
			w.writeMapStart();
			for(Entry<String, String> e1 : e.getValue().entrySet()) {
				w.writeString(e1.getKey());
				w.writeString(e1.getValue());
			}
			w.writeMapEnd();
		}
		w.writeMapEnd();
		w.writeInt64(System.currentTimeMillis());
		out.flush();
	}
}
