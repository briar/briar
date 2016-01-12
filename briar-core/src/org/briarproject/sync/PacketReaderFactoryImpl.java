package org.briarproject.sync;

import org.briarproject.api.data.BdfReaderFactory;
import org.briarproject.api.data.ObjectReader;
import org.briarproject.api.sync.PacketReader;
import org.briarproject.api.sync.PacketReaderFactory;
import org.briarproject.api.sync.SubscriptionUpdate;
import org.briarproject.api.sync.UnverifiedMessage;

import java.io.InputStream;

import javax.inject.Inject;

class PacketReaderFactoryImpl implements PacketReaderFactory {

	private final BdfReaderFactory bdfReaderFactory;
	private final ObjectReader<UnverifiedMessage> messageReader;
	private final ObjectReader<SubscriptionUpdate> subscriptionUpdateReader;

	@Inject
	PacketReaderFactoryImpl(BdfReaderFactory bdfReaderFactory,
			ObjectReader<UnverifiedMessage> messageReader,
			ObjectReader<SubscriptionUpdate> subscriptionUpdateReader) {
		this.bdfReaderFactory = bdfReaderFactory;
		this.messageReader = messageReader;
		this.subscriptionUpdateReader = subscriptionUpdateReader;
	}

	public PacketReader createPacketReader(InputStream in) {
		return new PacketReaderImpl(bdfReaderFactory, messageReader,
				subscriptionUpdateReader, in);
	}
}
