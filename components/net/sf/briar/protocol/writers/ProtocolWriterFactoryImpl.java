package net.sf.briar.protocol.writers;

import java.io.OutputStream;
import java.security.MessageDigest;

import net.sf.briar.api.crypto.CryptoComponent;
import net.sf.briar.api.protocol.writers.AckWriter;
import net.sf.briar.api.protocol.writers.BatchWriter;
import net.sf.briar.api.protocol.writers.OfferWriter;
import net.sf.briar.api.protocol.writers.ProtocolWriterFactory;
import net.sf.briar.api.protocol.writers.RequestWriter;
import net.sf.briar.api.protocol.writers.SubscriptionUpdateWriter;
import net.sf.briar.api.protocol.writers.TransportUpdateWriter;
import net.sf.briar.api.serial.SerialComponent;
import net.sf.briar.api.serial.WriterFactory;

import com.google.inject.Inject;

class ProtocolWriterFactoryImpl implements ProtocolWriterFactory {

	private final MessageDigest messageDigest;
	private final SerialComponent serial;
	private final WriterFactory writerFactory;

	@Inject
	ProtocolWriterFactoryImpl(CryptoComponent crypto,
			SerialComponent serial, WriterFactory writerFactory) {
		messageDigest = crypto.getMessageDigest();
		this.serial = serial;
		this.writerFactory = writerFactory;
	}

	public AckWriter createAckWriter(OutputStream out) {
		return new AckWriterImpl(out, serial, writerFactory);
	}

	public BatchWriter createBatchWriter(OutputStream out) {
		return new BatchWriterImpl(out, serial, writerFactory, messageDigest);
	}

	public OfferWriter createOfferWriter(OutputStream out) {
		return new OfferWriterImpl(out, serial, writerFactory);
	}

	public RequestWriter createRequestWriter(OutputStream out) {
		return new RequestWriterImpl(out, writerFactory);
	}

	public SubscriptionUpdateWriter createSubscriptionUpdateWriter(
			OutputStream out) {
		return new SubscriptionUpdateWriterImpl(out, writerFactory);
	}

	public TransportUpdateWriter createTransportUpdateWriter(OutputStream out) {
		return new TransportUpdateWriterImpl(out, writerFactory);
	}
}
