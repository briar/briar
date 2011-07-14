package net.sf.briar.protocol;

import java.io.IOException;
import java.io.OutputStream;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.Signature;
import java.util.Map;

import net.sf.briar.api.protocol.BatchId;
import net.sf.briar.api.protocol.BundleWriter;
import net.sf.briar.api.protocol.GroupId;
import net.sf.briar.api.serial.Raw;
import net.sf.briar.api.serial.Writer;
import net.sf.briar.api.serial.WriterFactory;

/** A bundle builder that serialises its contents using a writer. */
class BundleWriterImpl implements BundleWriter {

	private static enum State { START, FIRST_BATCH, MORE_BATCHES, END };

	private final SigningDigestingOutputStream out;
	private final Writer w;
	private final PrivateKey privateKey;
	private final Signature signature;
	private final MessageDigest messageDigest;
	private final long capacity;
	private State state = State.START;

	BundleWriterImpl(OutputStream out, WriterFactory writerFactory,
			PrivateKey privateKey, Signature signature,
			MessageDigest messageDigest, long capacity) {
		this.out =
			new SigningDigestingOutputStream(out, signature, messageDigest);
		w = writerFactory.createWriter(this.out);
		this.privateKey = privateKey;
		this.signature = signature;
		this.messageDigest = messageDigest;
		this.capacity = capacity;
	}

	public long getRemainingCapacity() {
		return capacity - w.getRawBytesWritten();
	}

	public void addHeader(Iterable<BatchId> acks, Iterable<GroupId> subs,
			Map<String, String> transports) throws IOException,
			GeneralSecurityException {
		if(state != State.START) throw new IllegalStateException();
		// Initialise the output stream
		signature.initSign(privateKey);
		// Write the data to be signed
		out.setSigning(true);
		w.writeListStart();
		for(BatchId ack : acks) w.writeRaw(ack);
		w.writeListEnd();
		w.writeListStart();
		for(GroupId sub : subs) w.writeRaw(sub);
		w.writeListEnd();
		w.writeMap(transports);
		w.writeInt64(System.currentTimeMillis());
		out.setSigning(false);
		// Create and write the signature
		byte[] sig = signature.sign();
		w.writeRaw(sig);
		// Expect a (possibly empty) list of batches
		state = State.FIRST_BATCH;
	}

	public BatchId addBatch(Iterable<Raw> messages) throws IOException,
	GeneralSecurityException {
		if(state == State.FIRST_BATCH) {
			w.writeListStart();
			state = State.MORE_BATCHES;
		}
		if(state != State.MORE_BATCHES) throw new IllegalStateException();
		// Initialise the output stream
		signature.initSign(privateKey);
		messageDigest.reset();
		// Write the data to be signed
		out.setDigesting(true);
		out.setSigning(true);
		w.writeListStart();
		for(Raw message : messages) w.writeRaw(message);
		w.writeListEnd();
		out.setSigning(false);
		// Create and write the signature
		byte[] sig = signature.sign();
		w.writeRaw(sig);
		out.setDigesting(false);
		// Calculate and return the ID
		return new BatchId(messageDigest.digest());
	}

	public void finish() throws IOException {
		if(state == State.FIRST_BATCH) {
			w.writeListStart();
			state = State.MORE_BATCHES;
		}
		if(state != State.MORE_BATCHES) throw new IllegalStateException();
		w.writeListEnd();
		w.close();
		state = State.END;
	}
}
