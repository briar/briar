package net.sf.briar.protocol.writers;

import java.io.IOException;
import java.io.OutputStream;

import net.sf.briar.api.protocol.MessageId;
import net.sf.briar.api.protocol.Offer;
import net.sf.briar.api.protocol.Tags;
import net.sf.briar.api.protocol.writers.OfferWriter;
import net.sf.briar.api.serial.Writer;
import net.sf.briar.api.serial.WriterFactory;

class OfferWriterImpl implements OfferWriter {

	private final OutputStream out;
	private final Writer w;

	private boolean started = false, finished = false;

	OfferWriterImpl(OutputStream out, WriterFactory writerFactory) {
		this.out = out;
		w = writerFactory.createWriter(out);
	}

	public boolean writeMessageId(MessageId m) throws IOException {
		if(finished) throw new IllegalStateException();
		if(!started) {
			w.writeUserDefinedTag(Tags.OFFER);
			w.writeListStart();
			started = true;
		}
		int capacity = Offer.MAX_SIZE - (int) w.getBytesWritten() - 1;
		if(capacity < MessageId.SERIALISED_LENGTH) return false;
		m.writeTo(w);
		return true;
	}

	public void finish() throws IOException {
		if(finished) throw new IllegalStateException();
		if(!started) {
			w.writeUserDefinedTag(Tags.OFFER);
			w.writeListStart();
			started = true;
		}
		w.writeListEnd();
		out.flush();
		finished = true;
	}
}
