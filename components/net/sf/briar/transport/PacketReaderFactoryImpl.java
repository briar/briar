package net.sf.briar.transport;

import java.io.InputStream;
import java.security.InvalidKeyException;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.SecretKey;

import net.sf.briar.api.crypto.CryptoComponent;
import net.sf.briar.api.protocol.Ack;
import net.sf.briar.api.protocol.Batch;
import net.sf.briar.api.protocol.Offer;
import net.sf.briar.api.protocol.Request;
import net.sf.briar.api.protocol.SubscriptionUpdate;
import net.sf.briar.api.protocol.TransportUpdate;
import net.sf.briar.api.serial.ObjectReader;
import net.sf.briar.api.serial.ReaderFactory;
import net.sf.briar.api.transport.PacketReader;
import net.sf.briar.api.transport.PacketReaderFactory;

import com.google.inject.Inject;
import com.google.inject.Provider;

class PacketReaderFactoryImpl implements PacketReaderFactory {

	private final CryptoComponent crypto;
	private final ReaderFactory readerFactory;
	private final Provider<ObjectReader<Ack>> ackProvider;
	private final Provider<ObjectReader<Batch>> batchProvider;
	private final Provider<ObjectReader<Offer>> offerProvider;
	private final Provider<ObjectReader<Request>> requestProvider;
	private final Provider<ObjectReader<SubscriptionUpdate>> subscriptionProvider;
	private final Provider<ObjectReader<TransportUpdate>> transportProvider;

	@Inject
	PacketReaderFactoryImpl(CryptoComponent crypto, ReaderFactory readerFactory,
			Provider<ObjectReader<Ack>> ackProvider,
			Provider<ObjectReader<Batch>> batchProvider,
			Provider<ObjectReader<Offer>> offerProvider,
			Provider<ObjectReader<Request>> requestProvider,
			Provider<ObjectReader<SubscriptionUpdate>> subscriptionProvider,
			Provider<ObjectReader<TransportUpdate>> transportProvider) {
		this.crypto = crypto;
		this.readerFactory = readerFactory;
		this.ackProvider = ackProvider;
		this.batchProvider = batchProvider;
		this.offerProvider = offerProvider;
		this.requestProvider = requestProvider;
		this.subscriptionProvider = subscriptionProvider;
		this.transportProvider = transportProvider;
	}

	public PacketReader createPacketReader(byte[] firstTag, InputStream in,
			int transportId, long connection, byte[] secret) {
		SecretKey macKey = crypto.deriveMacKey(secret);
		SecretKey tagKey = crypto.deriveTagKey(secret);
		SecretKey packetKey = crypto.derivePacketKey(secret);
		Cipher tagCipher = crypto.getTagCipher();
		Cipher packetCipher = crypto.getPacketCipher();
		Mac mac = crypto.getMac();
		try {
			mac.init(macKey);
		} catch(InvalidKeyException e) {
			throw new IllegalArgumentException(e);
		}
		PacketDecrypter decrypter = new PacketDecrypterImpl(firstTag, in,
				tagCipher, packetCipher, tagKey, packetKey);
		return new PacketReaderImpl(firstTag, readerFactory, ackProvider.get(),
				batchProvider.get(), offerProvider.get(), requestProvider.get(),
				subscriptionProvider.get(), transportProvider.get(),
				decrypter, mac, transportId, connection);
	}
}
