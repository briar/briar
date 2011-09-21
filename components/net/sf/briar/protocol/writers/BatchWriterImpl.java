package net.sf.briar.protocol.writers;

import java.io.IOException;
import java.io.OutputStream;
import java.security.DigestOutputStream;
import java.security.MessageDigest;

import net.sf.briar.api.protocol.BatchId;
import net.sf.briar.api.protocol.ProtocolConstants;
import net.sf.briar.api.protocol.Types;
import net.sf.briar.api.protocol.writers.BatchWriter;
import net.sf.briar.api.serial.SerialComponent;
import net.sf.briar.api.serial.Writer;
import net.sf.briar.api.serial.WriterFactory;

class BatchWriterImpl implements BatchWriter {

	private final DigestOutputStream out;
	private final int headerLength, footerLength;
	private final Writer w;
	private final MessageDigest messageDigest;

	private boolean started = false;
	private int capacity = ProtocolConstants.MAX_PACKET_LENGTH;

	BatchWriterImpl(OutputStream out, SerialComponent serial,
			WriterFactory writerFactory, MessageDigest messageDigest) {
		this.out = new DigestOutputStream(out, messageDigest);
		headerLength = serial.getSerialisedUserDefinedIdLength(Types.BATCH)
		+ serial.getSerialisedListStartLength();
		footerLength = serial.getSerialisedListEndLength();
		w = writerFactory.createWriter(this.out);
		this.messageDigest = messageDigest;
	}

	public void setMaxPacketLength(int length) {
		if(started) throw new IllegalStateException();
		if(length < 0 || length > ProtocolConstants.MAX_PACKET_LENGTH)
			throw new IllegalArgumentException();
		capacity = length;
	}

	public boolean writeMessage(byte[] message) throws IOException {
		int overhead = started ? footerLength : headerLength + footerLength;
		if(capacity < message.length + overhead) return false;
		if(!started) start();
		// Bypass the writer and write the raw message directly
		out.write(message);
		capacity -= message.length;
		return true;
	}

	public BatchId finish() throws IOException {
		if(!started) start();
		w.writeListEnd();
		out.flush();
		capacity = ProtocolConstants.MAX_PACKET_LENGTH;
		started = false;
		return new BatchId(messageDigest.digest());
	}

	private void start() throws IOException {
		messageDigest.reset();
		w.writeUserDefinedId(Types.BATCH);
		w.writeListStart();
		capacity -= headerLength;
		started = true;
	}
}
