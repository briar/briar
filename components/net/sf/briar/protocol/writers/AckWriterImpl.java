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
	private final int headerLength, idLength, footerLength;
	private final Writer w;

	private boolean started = false;
	private int capacity = ProtocolConstants.MAX_PACKET_LENGTH;

	AckWriterImpl(OutputStream out, SerialComponent serial,
			WriterFactory writerFactory) {
		this.out = out;
		headerLength = serial.getSerialisedUserDefinedIdLength(Types.ACK)
		+ serial.getSerialisedListStartLength();
		idLength = serial.getSerialisedUniqueIdLength(Types.BATCH_ID);
		footerLength = serial.getSerialisedListEndLength();
		w = writerFactory.createWriter(out);
	}

	public void setMaxPacketLength(int length) {
		if(started) throw new IllegalStateException();
		if(length < 0 || length > ProtocolConstants.MAX_PACKET_LENGTH)
			throw new IllegalArgumentException();
		capacity = length;
	}

	public boolean writeBatchId(BatchId b) throws IOException {
		int overhead = started ? footerLength : headerLength + footerLength;
		if(capacity < idLength + overhead) return false;
		if(!started) start();
		b.writeTo(w);
		capacity -= idLength;
		return true;
	}

	public void finish() throws IOException {
		if(!started) start();
		w.writeListEnd();
		out.flush();
		capacity = ProtocolConstants.MAX_PACKET_LENGTH;
		started = false;
	}

	private void start() throws IOException {
		w.writeUserDefinedId(Types.ACK);
		w.writeListStart();
		capacity -= headerLength;
		started = true;
	}
}
