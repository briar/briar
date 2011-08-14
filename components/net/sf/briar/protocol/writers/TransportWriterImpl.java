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

	public void writeTransportUpdate(
			Map<String, Map<String, String>> transports, long timestamp)
	throws IOException {
		w.writeUserDefinedTag(Tags.TRANSPORT_UPDATE);
		w.writeListStart();
		for(Entry<String, Map<String, String>> e : transports.entrySet()) {
			w.writeUserDefinedTag(Tags.TRANSPORT_PROPERTIES);
			w.writeString(e.getKey());
			w.writeMap(e.getValue());
		}
		w.writeListEnd();
		w.writeInt64(timestamp);
		out.flush();
	}
}
