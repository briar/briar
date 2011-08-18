package net.sf.briar.protocol.writers;

import java.io.IOException;
import java.io.OutputStream;

import net.sf.briar.api.protocol.BatchId;
import net.sf.briar.api.protocol.ProtocolConstants;
import net.sf.briar.api.protocol.Tags;
import net.sf.briar.api.protocol.writers.AckWriter;
import net.sf.briar.api.serial.Writer;
import net.sf.briar.api.serial.WriterFactory;

class AckWriterImpl implements AckWriter {

	private final OutputStream out;
	private final Writer w;

	private boolean started = false;

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
		int capacity = ProtocolConstants.MAX_PACKET_LENGTH
		- (int) w.getBytesWritten() - 1;
		// Allow one byte for the BATCH_ID tag, one byte for the BYTES tag and
		// one byte for the length as a uint7
		if(capacity < BatchId.LENGTH + 3) return false;
		b.writeTo(w);
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
