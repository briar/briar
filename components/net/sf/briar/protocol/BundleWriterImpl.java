package net.sf.briar.protocol;

import java.io.IOException;
import java.io.OutputStream;
import java.security.DigestOutputStream;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.Collection;
import java.util.Map;

import net.sf.briar.api.protocol.BatchId;
import net.sf.briar.api.protocol.BundleWriter;
import net.sf.briar.api.protocol.GroupId;
import net.sf.briar.api.protocol.Tags;
import net.sf.briar.api.serial.Raw;
import net.sf.briar.api.serial.Writer;
import net.sf.briar.api.serial.WriterFactory;

class BundleWriterImpl implements BundleWriter {

	private static enum State { START, FIRST_BATCH, MORE_BATCHES, END };

	private final DigestOutputStream out;
	private final Writer writer;
	private final MessageDigest messageDigest;
	private final long capacity;
	private State state = State.START;

	BundleWriterImpl(OutputStream out, WriterFactory writerFactory,
			MessageDigest messageDigest, long capacity) {
		this.out = new DigestOutputStream(out, messageDigest);
		this.out.on(false); // Turn off the digest until we need it
		writer = writerFactory.createWriter(this.out);
		this.messageDigest = messageDigest;
		this.capacity = capacity;
	}

	public long getRemainingCapacity() {
		return capacity - writer.getBytesWritten();
	}

	public void addHeader(Collection<BatchId> acks, Collection<GroupId> subs,
			Map<String, String> transports) throws IOException,
			GeneralSecurityException {
		if(state != State.START) throw new IllegalStateException();
		// Write the initial tag
		writer.writeUserDefinedTag(Tags.HEADER);
		// Write the data
		writer.writeList(acks);
		writer.writeList(subs);
		writer.writeMap(transports);
		writer.writeInt64(System.currentTimeMillis());
		// Expect a (possibly empty) list of batches
		state = State.FIRST_BATCH;
	}

	public BatchId addBatch(Collection<Raw> messages) throws IOException,
	GeneralSecurityException {
		if(state == State.FIRST_BATCH) {
			writer.writeListStart();
			state = State.MORE_BATCHES;
		}
		if(state != State.MORE_BATCHES) throw new IllegalStateException();
		// Write the initial tag
		writer.writeUserDefinedTag(Tags.BATCH);
		// Start digesting
		messageDigest.reset();
		out.on(true);
		// Write the data
		writer.writeListStart();
		// Bypass the writer and write each raw message directly
		for(Raw message : messages) {
			writer.writeUserDefinedTag(Tags.MESSAGE);
			out.write(message.getBytes());
		}
		writer.writeListEnd();
		// Stop digesting
		out.on(false);
		// Calculate and return the ID
		return new BatchId(messageDigest.digest());
	}

	public void finish() throws IOException {
		if(state == State.FIRST_BATCH) {
			writer.writeListStart();
			state = State.MORE_BATCHES;
		}
		if(state != State.MORE_BATCHES) throw new IllegalStateException();
		writer.writeListEnd();
		writer.close();
		state = State.END;
	}
}
