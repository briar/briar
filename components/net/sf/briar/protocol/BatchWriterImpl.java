package net.sf.briar.protocol;

import java.io.IOException;
import java.io.OutputStream;
import java.security.DigestOutputStream;
import java.security.MessageDigest;

import net.sf.briar.api.protocol.Batch;
import net.sf.briar.api.protocol.BatchId;
import net.sf.briar.api.protocol.BatchWriter;
import net.sf.briar.api.protocol.Tags;
import net.sf.briar.api.serial.Writer;
import net.sf.briar.api.serial.WriterFactory;

class BatchWriterImpl implements BatchWriter {

	private final DigestOutputStream out;
	private final Writer w;
	private final MessageDigest messageDigest;

	private boolean started = false, finished = false;

	BatchWriterImpl(OutputStream out, WriterFactory writerFactory,
			MessageDigest messageDigest) {
		this.out = new DigestOutputStream(out, messageDigest);
		w = writerFactory.createWriter(this.out);
		this.messageDigest = messageDigest;
	}

	public int getCapacity() {
		return Batch.MAX_SIZE - 3;
	}

	public boolean addMessage(byte[] message) throws IOException {
		if(finished) throw new IllegalStateException();
		if(!started) {
			messageDigest.reset();
			w.writeUserDefinedTag(Tags.BATCH);
			w.writeListStart();
			started = true;
		}
		int capacity = Batch.MAX_SIZE - (int) w.getBytesWritten() - 1;
		if(capacity < message.length) return false;
		// Bypass the writer and write each raw message directly
		out.write(message);
		return true;
	}

	public BatchId finish() throws IOException {
		if(finished) throw new IllegalStateException();
		if(!started) {
			messageDigest.reset();
			w.writeUserDefinedTag(Tags.BATCH);
			w.writeListStart();
			started = true;
		}
		w.writeListEnd();
		out.flush();
		finished = true;
		return new BatchId(messageDigest.digest());
	}
}
