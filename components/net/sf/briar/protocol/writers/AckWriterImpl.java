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

	private boolean started = false, finished = false;

	AckWriterImpl(OutputStream out, WriterFactory writerFactory) {
		this.out = out;
		w = writerFactory.createWriter(out);
	}

	public boolean writeBatchId(BatchId b) throws IOException {
		if(finished) throw new IllegalStateException();
		if(!started) {
			w.writeUserDefinedTag(Tags.ACK);
			w.writeListStart();
			started = true;
		}
		int capacity = Ack.MAX_SIZE - (int) w.getBytesWritten() - 1;
		if(capacity < BatchId.SERIALISED_LENGTH) return false;
		b.writeTo(w);
		return true;
	}

	public void finish() throws IOException {
		if(finished) throw new IllegalStateException();
		if(!started) {
			w.writeUserDefinedTag(Tags.ACK);
			w.writeListStart();
			started = true;
		}
		w.writeListEnd();
		out.flush();
		finished = true;
	}
}
