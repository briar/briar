package net.sf.briar.protocol.writers;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Map;

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

	public void writeTransports(Map<String, String> transports)
	throws IOException {
		w.writeUserDefinedTag(Tags.TRANSPORTS);
		w.writeMap(transports);
		w.writeInt64(System.currentTimeMillis());
		out.flush();
	}
}
