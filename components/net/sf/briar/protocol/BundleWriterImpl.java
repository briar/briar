package net.sf.briar.protocol;

import java.io.IOException;

import net.sf.briar.api.protocol.Batch;
import net.sf.briar.api.protocol.BatchId;
import net.sf.briar.api.protocol.BundleWriter;
import net.sf.briar.api.protocol.GroupId;
import net.sf.briar.api.protocol.Header;
import net.sf.briar.api.protocol.Message;
import net.sf.briar.api.serial.Writer;

/** A bundle builder that serialises its contents using a writer. */
class BundleWriterImpl implements BundleWriter {

	private static enum State { START, FIRST_BATCH, MORE_BATCHES, END };

	private final Writer w;
	private final long capacity;
	private State state = State.START;

	BundleWriterImpl(Writer w, long capacity) {
		this.w = w;
		this.capacity = capacity;
	}

	public long getCapacity() {
		return capacity;
	}

	public void addHeader(Header h) throws IOException {
		if(state != State.START) throw new IllegalStateException();
		w.writeListStart();
		for(BatchId ack : h.getAcks()) w.writeRaw(ack);
		w.writeListEnd();
		w.writeListStart();
		for(GroupId sub : h.getSubscriptions()) w.writeRaw(sub);
		w.writeListEnd();
		w.writeMap(h.getTransports());
		w.writeRaw(h.getSignature());
		state = State.FIRST_BATCH;
	}

	public void addBatch(Batch b) throws IOException {
		if(state == State.FIRST_BATCH) {
			w.writeListStart();
			state = State.MORE_BATCHES;
		}
		if(state != State.MORE_BATCHES) throw new IllegalStateException();
		w.writeListStart();
		for(Message m : b.getMessages()) w.writeRaw(m.getBytes());
		w.writeListEnd();
		w.writeRaw(b.getSignature());
	}

	public void close() throws IOException {
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
