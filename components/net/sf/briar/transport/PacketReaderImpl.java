package net.sf.briar.transport;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;

import javax.crypto.Mac;

import net.sf.briar.api.protocol.Ack;
import net.sf.briar.api.protocol.Batch;
import net.sf.briar.api.protocol.Offer;
import net.sf.briar.api.protocol.Request;
import net.sf.briar.api.protocol.SubscriptionUpdate;
import net.sf.briar.api.protocol.Tags;
import net.sf.briar.api.protocol.TransportUpdate;
import net.sf.briar.api.serial.FormatException;
import net.sf.briar.api.serial.ObjectReader;
import net.sf.briar.api.serial.Reader;
import net.sf.briar.api.serial.ReaderFactory;
import net.sf.briar.api.transport.PacketReader;

class PacketReaderImpl implements PacketReader {

	private final Reader reader;
	private final PacketDecrypter decrypter;
	private final Mac mac;
	private final int macLength, transportId;
	private final long connection;

	private long packet = 0L;
	private boolean betweenPackets = true;

	PacketReaderImpl(byte[] firstTag, ReaderFactory readerFactory,
			ObjectReader<Ack> ackReader, ObjectReader<Batch> batchReader,
			ObjectReader<Offer> offerReader,
			ObjectReader<Request> requestReader,
			ObjectReader<SubscriptionUpdate> subscriptionReader,
			ObjectReader<TransportUpdate> transportReader,
			PacketDecrypter decrypter, Mac mac, int transportId,
			long connection) {
		InputStream in = decrypter.getInputStream();
		reader = readerFactory.createReader(in);
		reader.addObjectReader(Tags.ACK, ackReader);
		reader.addObjectReader(Tags.BATCH, batchReader);
		reader.addObjectReader(Tags.OFFER, offerReader);
		reader.addObjectReader(Tags.REQUEST, requestReader);
		reader.addObjectReader(Tags.SUBSCRIPTIONS, subscriptionReader);
		reader.addObjectReader(Tags.TRANSPORTS, transportReader);
		reader.addConsumer(new MacConsumer(mac));
		this.decrypter = decrypter;
		this.mac = mac;
		macLength = mac.getMacLength();
		this.transportId = transportId;
		this.connection = connection;
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
			throw new FormatException();
		mac.update(tag);
		packet++;
		betweenPackets = false;
	}

	public Ack readAck() throws IOException {
		if(betweenPackets) readTag();
		Ack a = reader.readUserDefined(Tags.ACK, Ack.class);
		readMac();
		betweenPackets = true;
		return a;
	}

	private void readMac() throws IOException {
		byte[] expectedMac = mac.doFinal();
		byte[] actualMac = new byte[macLength];
		InputStream in = decrypter.getInputStream();
		int offset = 0;
		while(offset < macLength) {
			int read = in.read(actualMac, offset, actualMac.length - offset);
			if(read == -1) break;
			offset += read;
		}
		if(offset < macLength) throw new FormatException();
		if(!Arrays.equals(expectedMac, actualMac)) throw new FormatException();
	}

	public boolean hasBatch() throws IOException {
		if(betweenPackets) readTag();
		return reader.hasUserDefined(Tags.BATCH);
	}

	public Batch readBatch() throws IOException {
		if(betweenPackets) readTag();
		Batch b = reader.readUserDefined(Tags.BATCH, Batch.class);
		readMac();
		betweenPackets = true;
		return b;
	}

	public boolean hasOffer() throws IOException {
		if(betweenPackets) readTag();
		return reader.hasUserDefined(Tags.OFFER);
	}

	public Offer readOffer() throws IOException {
		if(betweenPackets) readTag();
		Offer o = reader.readUserDefined(Tags.OFFER, Offer.class);
		readMac();
		betweenPackets = true;
		return o;
	}

	public boolean hasRequest() throws IOException {
		if(betweenPackets) readTag();
		return reader.hasUserDefined(Tags.REQUEST);
	}

	public Request readRequest() throws IOException {
		if(betweenPackets) readTag();
		Request r = reader.readUserDefined(Tags.REQUEST, Request.class);
		readMac();
		betweenPackets = true;
		return r;
	}

	public boolean hasSubscriptionUpdate() throws IOException {
		if(betweenPackets) readTag();
		return reader.hasUserDefined(Tags.SUBSCRIPTIONS);
	}

	public SubscriptionUpdate readSubscriptionUpdate() throws IOException {
		if(betweenPackets) readTag();
		SubscriptionUpdate s = reader.readUserDefined(Tags.SUBSCRIPTIONS,
				SubscriptionUpdate.class);
		readMac();
		betweenPackets = true;
		return s;
	}

	public boolean hasTransportUpdate() throws IOException {
		if(betweenPackets) readTag();
		return reader.hasUserDefined(Tags.TRANSPORTS);
	}

	public TransportUpdate readTransportUpdate() throws IOException {
		if(betweenPackets) readTag();
		TransportUpdate t = reader.readUserDefined(Tags.TRANSPORTS,
				TransportUpdate.class);
		readMac();
		betweenPackets = true;
		return t;
	}
}
