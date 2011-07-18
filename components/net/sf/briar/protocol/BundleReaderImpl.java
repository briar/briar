package net.sf.briar.protocol;

import java.io.IOException;
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
import net.sf.briar.api.protocol.BundleReader;
import net.sf.briar.api.protocol.GroupId;
import net.sf.briar.api.protocol.Header;
import net.sf.briar.api.protocol.Message;
import net.sf.briar.api.protocol.MessageParser;
import net.sf.briar.api.protocol.UniqueId;
import net.sf.briar.api.protocol.Tags;
import net.sf.briar.api.serial.FormatException;
import net.sf.briar.api.serial.Raw;
import net.sf.briar.api.serial.RawByteArray;
import net.sf.briar.api.serial.Reader;

class BundleReaderImpl implements BundleReader {

	private static enum State { START, FIRST_BATCH, MORE_BATCHES, END };

	private final Reader reader;
	private final PublicKey publicKey;
	private final Signature signature;
	private final MessageDigest messageDigest;
	private final MessageParser messageParser;
	private final HeaderFactory headerFactory;
	private final BatchFactory batchFactory;
	private State state = State.START;

	BundleReaderImpl(Reader reader, PublicKey publicKey, Signature signature,
			MessageDigest messageDigest, MessageParser messageParser,
			HeaderFactory headerFactory, BatchFactory batchFactory) {
		this.reader = reader;
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
		CountingConsumer counting = new CountingConsumer(Header.MAX_SIZE);
		SigningConsumer signing = new SigningConsumer(signature);
		signature.initVerify(publicKey);
		// Read the signed data
		reader.addConsumer(counting);
		reader.addConsumer(signing);
		reader.readUserDefinedTag(Tags.HEADER);
		// Acks
		Set<BatchId> acks = new HashSet<BatchId>();
		reader.readListStart();
		while(!reader.hasListEnd()) {
			reader.readUserDefinedTag(Tags.BATCH_ID);
			byte[] b = reader.readRaw();
			if(b.length != UniqueId.LENGTH) throw new FormatException();
			acks.add(new BatchId(b));
		}
		reader.readListEnd();
		// Subs
		Set<GroupId> subs = new HashSet<GroupId>();
		reader.readListStart();
		while(!reader.hasListEnd()) {
			reader.readUserDefinedTag(Tags.GROUP_ID);
			byte[] b = reader.readRaw();
			if(b.length != UniqueId.LENGTH) throw new FormatException();
			subs.add(new GroupId(b));
		}
		reader.readListEnd();
		// Transports
		Map<String, String> transports =
			reader.readMap(String.class, String.class);
		// Timestamp
		reader.readUserDefinedTag(Tags.TIMESTAMP);
		long timestamp = reader.readInt64();
		reader.removeConsumer(signing);
		// Read and verify the signature
		reader.readUserDefinedTag(Tags.SIGNATURE);
		byte[] sig = reader.readRaw();
		reader.removeConsumer(counting);
		if(!signature.verify(sig)) throw new SignatureException();
		// Build and return the header
		return headerFactory.createHeader(acks, subs, transports, timestamp);
	}

	public Batch getNextBatch() throws IOException, GeneralSecurityException {
		if(state == State.FIRST_BATCH) {
			reader.readListStart();
			state = State.MORE_BATCHES;
		}
		if(state != State.MORE_BATCHES) throw new IllegalStateException();
		if(reader.hasListEnd()) {
			reader.readListEnd();
			// That should be all
			if(!reader.eof()) throw new FormatException();
			state = State.END;
			return null;
		}
		// Initialise the input stream
		CountingConsumer counting = new CountingConsumer(Batch.MAX_SIZE);
		DigestingConsumer digesting = new DigestingConsumer(messageDigest);
		messageDigest.reset();
		SigningConsumer signing = new SigningConsumer(signature);
		signature.initVerify(publicKey);
		// Read the signed data
		reader.addConsumer(counting);
		reader.addConsumer(digesting);
		reader.addConsumer(signing);
		reader.readUserDefinedTag(Tags.BATCH);
		List<Raw> rawMessages = new ArrayList<Raw>();
		reader.readListStart();
		while(!reader.hasListEnd()) {
			reader.readUserDefinedTag(Tags.MESSAGE);
			rawMessages.add(new RawByteArray(reader.readRaw()));
		}
		reader.readListEnd();
		reader.removeConsumer(signing);
		// Read and verify the signature
		reader.readUserDefinedTag(Tags.SIGNATURE);
		byte[] sig = reader.readRaw();
		reader.removeConsumer(digesting);
		reader.removeConsumer(counting);
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
		reader.close();
	}
}
