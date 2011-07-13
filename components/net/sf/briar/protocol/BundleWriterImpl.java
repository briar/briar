package net.sf.briar.protocol;

import java.io.IOException;
import java.io.OutputStream;
import java.security.DigestOutputStream;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.Signature;
import java.util.Map;

import net.sf.briar.api.protocol.BatchId;
import net.sf.briar.api.protocol.BundleId;
import net.sf.briar.api.protocol.BundleWriter;
import net.sf.briar.api.protocol.GroupId;
import net.sf.briar.api.protocol.Message;
import net.sf.briar.api.serial.Writer;
import net.sf.briar.api.serial.WriterFactory;

/** A bundle builder that serialises its contents using a writer. */
class BundleWriterImpl implements BundleWriter {

	private static enum State { START, FIRST_BATCH, MORE_BATCHES, END };

	private final SigningOutputStream out;
	private final Writer w;
	private final PrivateKey privateKey;
	private final Signature signature;
	private final MessageDigest messageDigest;
	private final long capacity;
	private State state = State.START;

	BundleWriterImpl(OutputStream out, WriterFactory writerFactory,
			PrivateKey privateKey, Signature signature,
			MessageDigest messageDigest, long capacity) {
		OutputStream out1 = new DigestOutputStream(out, messageDigest);
		this.out = new SigningOutputStream(out1, signature);
		w = writerFactory.createWriter(this.out);
		this.privateKey = privateKey;
		this.signature = signature;
		this.messageDigest = messageDigest;
		this.capacity = capacity;
	}

	public long getRemainingCapacity() {
		return capacity - w.getRawBytesWritten();
	}

	public BundleId addHeader(Iterable<BatchId> acks, Iterable<GroupId> subs,
			Map<String, String> transports) throws IOException,
			GeneralSecurityException {
		if(state != State.START) throw new IllegalStateException();
		// Initialise the output stream
		signature.initSign(privateKey);
		messageDigest.reset();
		// Write the data to be signed
		out.setSigning(true);
		w.writeListStart();
		for(BatchId ack : acks) w.writeRaw(ack);
		w.writeListEnd();
		w.writeListStart();
		for(GroupId sub : subs) w.writeRaw(sub);
		w.writeListEnd();
		w.writeMap(transports);
		out.setSigning(false);
		// Create and write the signature
		byte[] sig = signature.sign();
		w.writeRaw(sig);
		// Calculate and return the ID
		state = State.FIRST_BATCH;
		return new BundleId(messageDigest.digest());
	}

	public BatchId addBatch(Iterable<Message> messages) throws IOException,
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
		out.setSigning(true);
		w.writeListStart();
		for(Message m : messages) w.writeRaw(m);
		w.writeListEnd();
		out.setSigning(false);
		// Create and write the signature
		byte[] sig = signature.sign();
		w.writeRaw(sig);
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
