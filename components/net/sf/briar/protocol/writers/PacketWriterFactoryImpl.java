package net.sf.briar.protocol.writers;

import java.io.OutputStream;
import java.security.MessageDigest;

import net.sf.briar.api.protocol.writers.AckWriter;
import net.sf.briar.api.protocol.writers.BatchWriter;
import net.sf.briar.api.protocol.writers.PacketWriterFactory;
import net.sf.briar.api.protocol.writers.SubscriptionWriter;
import net.sf.briar.api.protocol.writers.TransportWriter;
import net.sf.briar.api.serial.WriterFactory;

import com.google.inject.Inject;

class PacketWriterFactoryImpl implements PacketWriterFactory {

	private final MessageDigest messageDigest;
	private final WriterFactory writerFactory;

	@Inject
	PacketWriterFactoryImpl(MessageDigest messageDigest,
			WriterFactory writerFactory) {
		this.messageDigest = messageDigest;
		this.writerFactory = writerFactory;
	}

	public AckWriter createAckWriter(OutputStream out) {
		return new AckWriterImpl(out, writerFactory);
	}

	public BatchWriter createBatchWriter(OutputStream out) {
		return new BatchWriterImpl(out, writerFactory, messageDigest);
	}

	public SubscriptionWriter createSubscriptionWriter(OutputStream out) {
		return new SubscriptionWriterImpl(out, writerFactory);
	}

	public TransportWriter createTransportWriter(OutputStream out) {
		return new TransportWriterImpl(out, writerFactory);
	}
}
