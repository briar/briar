package net.sf.briar.protocol.writers;

import java.io.IOException;
import java.io.OutputStream;

import net.sf.briar.api.protocol.BatchId;
import net.sf.briar.api.protocol.ProtocolConstants;
import net.sf.briar.api.protocol.Types;
import net.sf.briar.api.protocol.writers.AckWriter;
import net.sf.briar.api.serial.SerialComponent;
import net.sf.briar.api.serial.Writer;
import net.sf.briar.api.serial.WriterFactory;

class AckWriterImpl implements AckWriter {

	private final OutputStream out;
	private final SerialComponent serial;
	private final Writer w;

	private boolean started = false;
	private int capacity = ProtocolConstants.MAX_PACKET_LENGTH; // FIXME

	AckWriterImpl(OutputStream out, SerialComponent serial,
			WriterFactory writerFactory) {
		this.out = out;
		this.serial = serial;
		w = writerFactory.createWriter(out);
	}

	public boolean writeBatchId(BatchId b) throws IOException {
		if(!started) start();
		int length = serial.getSerialisedUniqueIdLength(Types.BATCH_ID);
		if(capacity < length + serial.getSerialisedListEndLength())
			return false;
		b.writeTo(w);
		capacity -= length;
		return true;
	}

	public void finish() throws IOException {
		if(!started) start();
		w.writeListEnd();
		out.flush();
		capacity = ProtocolConstants.MAX_PACKET_LENGTH; // FIXME
		started = false;
	}

	private void start() throws IOException {
		w.writeUserDefinedTag(Types.ACK);
		capacity -= serial.getSerialisedUserDefinedIdLength(Types.ACK);
		w.writeListStart();
		capacity -= serial.getSerialisedListStartLength();
		started = true;
	}
}
