package net.sf.briar.protocol.writers;

import java.io.IOException;
import java.io.OutputStream;

import net.sf.briar.api.protocol.Ack;
import net.sf.briar.api.protocol.BatchId;
import net.sf.briar.api.protocol.Tags;
import net.sf.briar.api.protocol.writers.AckWriter;
import net.sf.briar.api.serial.Writer;
import net.sf.briar.api.serial.WriterFactory;

class AckWriterImpl implements AckWriter {

	private final OutputStream out;
	private final Writer w;

	private boolean started = false;
	private int idsWritten = 0;

	AckWriterImpl(OutputStream out, WriterFactory writerFactory) {
		this.out = out;
		w = writerFactory.createWriter(out);
	}

	public boolean writeBatchId(BatchId b) throws IOException {
		if(!started) {
			w.writeUserDefinedTag(Tags.ACK);
			w.writeListStart();
			started = true;
		}
		if(idsWritten >= Ack.MAX_IDS_PER_ACK) return false;
		b.writeTo(w);
		idsWritten++;
		return true;
	}

	public void finish() throws IOException {
		if(!started) {
			w.writeUserDefinedTag(Tags.ACK);
			w.writeListStart();
			started = true;
		}
		w.writeListEnd();
		out.flush();
		started = false;
	}
}
