package net.sf.briar.protocol;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.sf.briar.api.protocol.Batch;
import net.sf.briar.api.protocol.BatchBuilder;
import net.sf.briar.api.protocol.BatchId;
import net.sf.briar.api.protocol.BundleReader;
import net.sf.briar.api.protocol.GroupId;
import net.sf.briar.api.protocol.Header;
import net.sf.briar.api.protocol.HeaderBuilder;
import net.sf.briar.api.protocol.Message;
import net.sf.briar.api.protocol.MessageParser;
import net.sf.briar.api.protocol.UniqueId;
import net.sf.briar.api.serial.FormatException;
import net.sf.briar.api.serial.Raw;
import net.sf.briar.api.serial.Reader;

import com.google.inject.Provider;

/** A bundle that deserialises its contents on demand using a reader. */
class BundleReaderImpl implements BundleReader {

	private static enum State { START, FIRST_BATCH, MORE_BATCHES, END };

	private final Reader r;
	private final long size;
	private final MessageParser messageParser;
	private final Provider<HeaderBuilder> headerBuilderProvider;
	private final Provider<BatchBuilder> batchBuilderProvider;
	private State state = State.START;

	BundleReaderImpl(Reader r, long size, MessageParser messageParser,
			Provider<HeaderBuilder> headerBuilderProvider,
			Provider<BatchBuilder> batchBuilderProvider) {
		this.r = r;
		this.size = size;
		this.messageParser = messageParser;
		this.headerBuilderProvider = headerBuilderProvider;
		this.batchBuilderProvider = batchBuilderProvider;
	}

	public long getSize() {
		return size;
	}

	public Header getHeader() throws IOException, GeneralSecurityException {
		if(state != State.START) throw new IllegalStateException();
		r.setReadLimit(Header.MAX_SIZE);
		Set<BatchId> acks = new HashSet<BatchId>();
		for(Raw raw : r.readList(Raw.class)) {
			byte[] b = raw.getBytes();
			if(b.length != UniqueId.LENGTH) throw new FormatException();
			acks.add(new BatchId(b));
		}
		Set<GroupId> subs = new HashSet<GroupId>();
		for(Raw raw : r.readList(Raw.class)) {
			byte[] b = raw.getBytes();
			if(b.length != UniqueId.LENGTH) throw new FormatException();
			subs.add(new GroupId(b));
		}
		Map<String, String> transports = r.readMap(String.class, String.class);
		byte[] sig = r.readRaw();
		state = State.FIRST_BATCH;
		HeaderBuilder h = headerBuilderProvider.get();
		h.addAcks(acks);
		h.addSubscriptions(subs);
		h.addTransports(transports);
		h.setSignature(sig);
		return h.build();
	}

	public Batch getNextBatch() throws IOException, GeneralSecurityException {
		if(state == State.FIRST_BATCH) {
			r.readListStart();
			state = State.MORE_BATCHES;
		}
		if(state != State.MORE_BATCHES) throw new IllegalStateException();
		if(r.hasListEnd()) {
			r.readListEnd();
			state = State.END;
			return null;
		}
		r.setReadLimit(Batch.MAX_SIZE);
		List<Raw> messages = r.readList(Raw.class);
		BatchBuilder b = batchBuilderProvider.get();
		for(Raw r : messages) {
			Message m = messageParser.parseMessage(r.getBytes());
			b.addMessage(m);
		}
		byte[] sig = r.readRaw();
		b.setSignature(sig);
		return b.build();
	}

	public void close() throws IOException {
		r.close();
	}
}
