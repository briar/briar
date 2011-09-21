package net.sf.briar.protocol.writers;

import java.io.IOException;
import java.io.OutputStream;

import net.sf.briar.api.protocol.MessageId;
import net.sf.briar.api.protocol.ProtocolConstants;
import net.sf.briar.api.protocol.Types;
import net.sf.briar.api.protocol.writers.OfferWriter;
import net.sf.briar.api.serial.SerialComponent;
import net.sf.briar.api.serial.Writer;
import net.sf.briar.api.serial.WriterFactory;

class OfferWriterImpl implements OfferWriter {

	private final OutputStream out;
	private final SerialComponent serial;
	private final Writer w;

	private boolean started = false;
	private int capacity = ProtocolConstants.MAX_PACKET_LENGTH; // FIXME

	OfferWriterImpl(OutputStream out, SerialComponent serial,
			WriterFactory writerFactory) {
		this.out = out;
		this.serial = serial;
		w = writerFactory.createWriter(out);
	}

	public boolean writeMessageId(MessageId m) throws IOException {
		if(!started) start();
		int length = serial.getSerialisedUniqueIdLength(Types.MESSAGE_ID);
		if(capacity <  length + serial.getSerialisedListEndLength())
			return false;
		m.writeTo(w);
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
		w.writeUserDefinedTag(Types.OFFER);
		w.writeListStart();
		started = true;
	}
}
