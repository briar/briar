package net.sf.briar.protocol.writers;

import java.io.IOException;
import java.io.OutputStream;
import java.security.DigestOutputStream;
import java.security.MessageDigest;

import net.sf.briar.api.protocol.MessageId;
import net.sf.briar.api.protocol.Offer;
import net.sf.briar.api.protocol.OfferId;
import net.sf.briar.api.protocol.Tags;
import net.sf.briar.api.protocol.writers.OfferWriter;
import net.sf.briar.api.serial.Writer;
import net.sf.briar.api.serial.WriterFactory;

class OfferWriterImpl implements OfferWriter {

	private final OutputStream out;
	private final Writer w;
	private final MessageDigest messageDigest;

	private boolean started = false;
	private int idsWritten = 0;

	OfferWriterImpl(OutputStream out, WriterFactory writerFactory,
			MessageDigest messageDigest) {
		this.out = new DigestOutputStream(out, messageDigest);
		w = writerFactory.createWriter(out);
		this.messageDigest = messageDigest;
	}

	public boolean writeMessageId(MessageId m) throws IOException {
		if(!started) {
			messageDigest.reset();
			w.writeUserDefinedTag(Tags.OFFER);
			w.writeListStart();
			started = true;
		}
		if(idsWritten >= Offer.MAX_IDS_PER_OFFER) return false;
		m.writeTo(w);
		idsWritten++;
		return true;
	}

	public OfferId finish() throws IOException {
		if(!started) {
			messageDigest.reset();
			w.writeUserDefinedTag(Tags.OFFER);
			w.writeListStart();
			started = true;
		}
		w.writeListEnd();
		out.flush();
		started = false;
		return new OfferId(messageDigest.digest());
	}
}
