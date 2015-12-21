package org.briarproject.sync;

import org.briarproject.api.crypto.CryptoComponent;
import org.briarproject.api.data.BdfReaderFactory;
import org.briarproject.api.data.ObjectReader;
import org.briarproject.api.sync.PacketReader;
import org.briarproject.api.sync.PacketReaderFactory;
import org.briarproject.api.sync.SubscriptionUpdate;

import java.io.InputStream;

import javax.inject.Inject;

class PacketReaderFactoryImpl implements PacketReaderFactory {

	private final CryptoComponent crypto;
	private final BdfReaderFactory bdfReaderFactory;
	private final ObjectReader<SubscriptionUpdate> subscriptionUpdateReader;

	@Inject
	PacketReaderFactoryImpl(CryptoComponent crypto,
			BdfReaderFactory bdfReaderFactory,
			ObjectReader<SubscriptionUpdate> subscriptionUpdateReader) {
		this.crypto = crypto;
		this.bdfReaderFactory = bdfReaderFactory;
		this.subscriptionUpdateReader = subscriptionUpdateReader;
	}

	public PacketReader createPacketReader(InputStream in) {
		return new PacketReaderImpl(crypto, bdfReaderFactory,
				subscriptionUpdateReader, in);
	}
}
