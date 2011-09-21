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
	private final SerialComponent serial;
	private final Writer w;
	private final MessageDigest messageDigest;

	private boolean started = false;
	private int capacity = ProtocolConstants.MAX_PACKET_LENGTH; // FIXME

	BatchWriterImpl(OutputStream out, SerialComponent serial,
			WriterFactory writerFactory, MessageDigest messageDigest) {
		this.out = new DigestOutputStream(out, messageDigest);
		this.serial = serial;
		w = writerFactory.createWriter(this.out);
		this.messageDigest = messageDigest;
	}

	public boolean writeMessage(byte[] message) throws IOException {
		if(!started) start();
		if(capacity < message.length + serial.getSerialisedListEndLength())
			return false;
		// Bypass the writer and write the raw message directly
		out.write(message);
		capacity -= message.length;
		return true;
	}

	public BatchId finish() throws IOException {
		if(!started) start();
		w.writeListEnd();
		out.flush();
		capacity = ProtocolConstants.MAX_PACKET_LENGTH; // FIXME
		started = false;
		return new BatchId(messageDigest.digest());
	}

	private void start() throws IOException {
		messageDigest.reset();
		w.writeUserDefinedTag(Types.BATCH);
		capacity -= serial.getSerialisedUserDefinedIdLength(Types.BATCH);
		w.writeListStart();
		capacity -= serial.getSerialisedListStartLength();
		started = true;
	}
}
