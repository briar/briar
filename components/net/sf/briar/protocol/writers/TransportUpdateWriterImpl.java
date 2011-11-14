package net.sf.briar.protocol.writers;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Collection;

import net.sf.briar.api.protocol.Transport;
import net.sf.briar.api.protocol.Types;
import net.sf.briar.api.protocol.writers.TransportUpdateWriter;
import net.sf.briar.api.serial.Writer;
import net.sf.briar.api.serial.WriterFactory;

class TransportUpdateWriterImpl implements TransportUpdateWriter {

	private final OutputStream out;
	private final Writer w;

	TransportUpdateWriterImpl(OutputStream out, WriterFactory writerFactory) {
		this.out = out;
		w = writerFactory.createWriter(out);
	}

	public void writeTransports(Collection<Transport> transports,
			long timestamp) throws IOException {
		w.writeUserDefinedId(Types.TRANSPORT_UPDATE);
		w.writeListStart();
		for(Transport p : transports) {
			w.writeUserDefinedId(Types.TRANSPORT);
			w.writeBytes(p.getId().getBytes());
			w.writeInt32(p.getIndex().getInt());
			w.writeMap(p);
		}
		w.writeListEnd();
		w.writeInt64(timestamp);
		out.flush();
	}
}
