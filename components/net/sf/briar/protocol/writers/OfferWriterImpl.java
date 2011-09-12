package net.sf.briar.protocol.writers;

import java.io.IOException;
import java.io.OutputStream;

import net.sf.briar.api.protocol.MessageId;
import net.sf.briar.api.protocol.Offer;
import net.sf.briar.api.protocol.Types;
import net.sf.briar.api.protocol.writers.OfferWriter;
import net.sf.briar.api.serial.Writer;
import net.sf.briar.api.serial.WriterFactory;

class OfferWriterImpl implements OfferWriter {

	private final OutputStream out;
	private final Writer w;

	private boolean started = false;
	private int idsWritten = 0;

	OfferWriterImpl(OutputStream out, WriterFactory writerFactory) {
		this.out = out;
		w = writerFactory.createWriter(out);
	}

	public boolean writeMessageId(MessageId m) throws IOException {
		if(!started) {
			w.writeUserDefinedTag(Types.OFFER);
			w.writeListStart();
			started = true;
		}
		if(idsWritten >= Offer.MAX_IDS_PER_OFFER) return false;
		m.writeTo(w);
		idsWritten++;
		return true;
	}

	public void finish() throws IOException {
		if(!started) {
			w.writeUserDefinedTag(Types.OFFER);
			w.writeListStart();
			started = true;
		}
		w.writeListEnd();
		out.flush();
		started = false;
	}
}
