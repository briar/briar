package net.sf.briar.transport;

import java.io.IOException;
import java.io.InputStream;

import javax.crypto.Mac;

import net.sf.briar.api.protocol.Ack;
import net.sf.briar.api.protocol.Batch;
import net.sf.briar.api.protocol.Offer;
import net.sf.briar.api.protocol.Request;
import net.sf.briar.api.protocol.SubscriptionUpdate;
import net.sf.briar.api.protocol.Tags;
import net.sf.briar.api.protocol.TransportUpdate;
import net.sf.briar.api.protocol.writers.ProtocolReaderFactory;
import net.sf.briar.api.serial.Reader;
import net.sf.briar.api.serial.ReaderFactory;
import net.sf.briar.api.transport.PacketReader;

class PacketReaderImpl implements PacketReader {

	private final Reader reader;
	private final PacketDecrypter decrypter;
	private final Mac mac;
	private final int transportId;
	private final long connection;

	private long packet = 0L;
	private boolean betweenPackets = true;

	PacketReaderImpl(byte[] firstTag, ReaderFactory readerFactory,
			ProtocolReaderFactory protocol, PacketDecrypter decrypter, Mac mac,
			int transportId, long connection) {
		InputStream in = decrypter.getInputStream();
		reader = readerFactory.createReader(in);
		reader.addObjectReader(Tags.ACK, protocol.createAckReader(in));
		reader.addObjectReader(Tags.BATCH, protocol.createBatchReader(in));
		reader.addObjectReader(Tags.OFFER, protocol.createOfferReader(in));
		reader.addObjectReader(Tags.REQUEST, protocol.createRequestReader(in));
		reader.addObjectReader(Tags.SUBSCRIPTIONS,
				protocol.createSubscriptionReader(in));
		reader.addObjectReader(Tags.TRANSPORTS,
				protocol.createTransportReader(in));
		this.decrypter = decrypter;
		this.mac = mac;
		this.transportId = transportId;
		this.connection = connection;
	}

	public boolean eof() throws IOException {
		return reader.eof();
	}

	public boolean hasAck() throws IOException {
		if(betweenPackets) readTag();
		return reader.hasUserDefined(Tags.ACK);
	}

	private void readTag() throws IOException {
		assert betweenPackets;
		if(packet > Constants.MAX_32_BIT_UNSIGNED)
			throw new IllegalStateException();
		byte[] tag = decrypter.readTag();
		if(!TagDecoder.decodeTag(tag, transportId, connection, packet))
			throw new IOException();
		mac.update(tag);
		packet++;
		betweenPackets = false;
	}

	public Ack readAck() throws IOException {
		if(betweenPackets) readTag();
		return reader.readUserDefined(Tags.ACK, Ack.class);
	}

	public boolean hasBatch() throws IOException {
		if(betweenPackets) readTag();
		return reader.hasUserDefined(Tags.BATCH);
	}

	public Batch readBatch() throws IOException {
		if(betweenPackets) readTag();
		return reader.readUserDefined(Tags.BATCH, Batch.class);
	}

	public boolean hasOffer() throws IOException {
		if(betweenPackets) readTag();
		return reader.hasUserDefined(Tags.OFFER);
	}

	public Offer readOffer() throws IOException {
		if(betweenPackets) readTag();
		return reader.readUserDefined(Tags.OFFER, Offer.class);
	}

	public boolean hasRequest() throws IOException {
		if(betweenPackets) readTag();
		return reader.hasUserDefined(Tags.REQUEST);
	}

	public Request readRequest() throws IOException {
		if(betweenPackets) readTag();
		return reader.readUserDefined(Tags.REQUEST, Request.class);
	}

	public boolean hasSubscriptionUpdate() throws IOException {
		if(betweenPackets) readTag();
		return reader.hasUserDefined(Tags.SUBSCRIPTIONS);
	}

	public SubscriptionUpdate readSubscriptionUpdate() throws IOException {
		if(betweenPackets) readTag();
		return reader.readUserDefined(Tags.SUBSCRIPTIONS,
				SubscriptionUpdate.class);
	}

	public boolean hasTransportUpdate() throws IOException {
		if(betweenPackets) readTag();
		return reader.hasUserDefined(Tags.TRANSPORTS);
	}

	public TransportUpdate readTransportUpdate() throws IOException {
		if(betweenPackets) readTag();
		return reader.readUserDefined(Tags.TRANSPORTS, TransportUpdate.class);
	}
}
