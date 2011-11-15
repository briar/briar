package net.sf.briar.protocol.writers;

import java.io.IOException;
import java.io.OutputStream;

import net.sf.briar.api.crypto.MessageDigest;
import net.sf.briar.api.protocol.BatchId;
import net.sf.briar.api.protocol.ProtocolConstants;
import net.sf.briar.api.protocol.Types;
import net.sf.briar.api.protocol.writers.BatchWriter;
import net.sf.briar.api.serial.DigestingConsumer;
import net.sf.briar.api.serial.SerialComponent;
import net.sf.briar.api.serial.Writer;
import net.sf.briar.api.serial.WriterFactory;

class BatchWriterImpl implements BatchWriter {

	private final OutputStream out;
	private final int headerLength, footerLength;
	private final Writer w;
	private final MessageDigest messageDigest;
	private final DigestingConsumer digestingConsumer;

	private boolean started = false;
	private int capacity = ProtocolConstants.MAX_PACKET_LENGTH;
	private int remaining = capacity;

	BatchWriterImpl(OutputStream out, SerialComponent serial,
			WriterFactory writerFactory, MessageDigest messageDigest) {
		this.out = out;
		headerLength = serial.getSerialisedUserDefinedIdLength(Types.BATCH)
		+ serial.getSerialisedListStartLength();
		footerLength = serial.getSerialisedListEndLength();
		w = writerFactory.createWriter(this.out);
		this.messageDigest = messageDigest;
		digestingConsumer = new DigestingConsumer(messageDigest);
	}

	public int getCapacity() {
		return capacity - headerLength - footerLength;
	}

	public void setMaxPacketLength(int length) {
		if(started) throw new IllegalStateException();
		if(length < 0 || length > ProtocolConstants.MAX_PACKET_LENGTH)
			throw new IllegalArgumentException();
		remaining = capacity = length;
	}

	public boolean writeMessage(byte[] message) throws IOException {
		int overhead = started ? footerLength : headerLength + footerLength;
		if(remaining < message.length + overhead) return false;
		if(!started) start();
		// Bypass the writer and write the raw message directly
		out.write(message);
		remaining -= message.length;
		return true;
	}

	public BatchId finish() throws IOException {
		if(!started) start();
		w.writeListEnd();
		w.removeConsumer(digestingConsumer);
		out.flush();
		remaining = capacity = ProtocolConstants.MAX_PACKET_LENGTH;
		started = false;
		return new BatchId(messageDigest.digest());
	}

	private void start() throws IOException {
		messageDigest.reset();
		w.addConsumer(digestingConsumer);
		w.writeUserDefinedId(Types.BATCH);
		w.writeListStart();
		remaining -= headerLength;
		started = true;
	}
}
