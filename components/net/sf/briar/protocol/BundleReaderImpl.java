package net.sf.briar.protocol;

import java.io.IOException;
import java.io.InputStream;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.PublicKey;
import java.security.Signature;
import java.security.SignatureException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.sf.briar.api.protocol.Batch;
import net.sf.briar.api.protocol.BatchId;
import net.sf.briar.api.protocol.BundleId;
import net.sf.briar.api.protocol.BundleReader;
import net.sf.briar.api.protocol.GroupId;
import net.sf.briar.api.protocol.Header;
import net.sf.briar.api.protocol.Message;
import net.sf.briar.api.protocol.MessageParser;
import net.sf.briar.api.protocol.UniqueId;
import net.sf.briar.api.serial.FormatException;
import net.sf.briar.api.serial.Raw;
import net.sf.briar.api.serial.Reader;
import net.sf.briar.api.serial.ReaderFactory;

/** A bundle that deserialises its contents on demand using a reader. */
class BundleReaderImpl implements BundleReader {

	private static enum State { START, FIRST_BATCH, MORE_BATCHES, END };

	private final SigningDigestingInputStream in;
	private final Reader r;
	private final PublicKey publicKey;
	private final Signature signature;
	private final MessageDigest messageDigest;
	private final MessageParser messageParser;
	private final HeaderFactory headerFactory;
	private final BatchFactory batchFactory;
	private State state = State.START;

	BundleReaderImpl(InputStream in, ReaderFactory readerFactory,
			PublicKey publicKey, Signature signature,
			MessageDigest messageDigest, MessageParser messageParser,
			HeaderFactory headerFactory, BatchFactory batchFactory) {
		this.in = new SigningDigestingInputStream(in, signature, messageDigest);
		r = readerFactory.createReader(this.in);
		this.publicKey = publicKey;
		this.signature = signature;
		this.messageDigest = messageDigest;
		this.messageParser = messageParser;
		this.headerFactory = headerFactory;
		this.batchFactory = batchFactory;
	}

	public Header getHeader() throws IOException, GeneralSecurityException {
		if(state != State.START) throw new IllegalStateException();
		state = State.FIRST_BATCH;
		// Initialise the input stream
		signature.initVerify(publicKey);
		messageDigest.reset();
		// Read the signed data
		in.setDigesting(true);
		in.setSigning(true);
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
		Map<String, String> transports =
			r.readMap(String.class, String.class);
		in.setSigning(false);
		// Read and verify the signature
		byte[] sig = r.readRaw();
		in.setDigesting(false);
		if(!signature.verify(sig)) throw new SignatureException();
		// Build and return the header
		BundleId id = new BundleId(messageDigest.digest());
		return headerFactory.createHeader(id, acks, subs, transports);
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
		// Initialise the input stream
		signature.initVerify(publicKey);
		messageDigest.reset();
		// Read the signed data
		in.setDigesting(true);
		in.setSigning(true);
		r.setReadLimit(Batch.MAX_SIZE);
		List<Raw> rawMessages = r.readList(Raw.class);
		in.setSigning(false);
		// Read and verify the signature
		byte[] sig = r.readRaw();
		in.setDigesting(false);
		if(!signature.verify(sig)) throw new SignatureException();
		// Parse the messages
		List<Message> messages = new ArrayList<Message>(rawMessages.size());
		for(Raw r : rawMessages) {
			Message m = messageParser.parseMessage(r.getBytes());
			messages.add(m);
		}
		// Build and return the batch
		BatchId id = new BatchId(messageDigest.digest());
		return batchFactory.createBatch(id, messages);
	}

	public void finish() throws IOException {
		r.close();
	}
}
