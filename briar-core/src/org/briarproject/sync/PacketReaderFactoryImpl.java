package org.briarproject.sync;

import org.briarproject.api.data.ObjectReader;
import org.briarproject.api.data.ReaderFactory;
import org.briarproject.api.sync.PacketReader;
import org.briarproject.api.sync.PacketReaderFactory;
import org.briarproject.api.sync.SubscriptionUpdate;
import org.briarproject.api.sync.UnverifiedMessage;

import java.io.InputStream;

import javax.inject.Inject;

class PacketReaderFactoryImpl implements PacketReaderFactory {

	private final ReaderFactory readerFactory;
	private final ObjectReader<UnverifiedMessage> messageReader;
	private final ObjectReader<SubscriptionUpdate> subscriptionUpdateReader;

	@Inject
	PacketReaderFactoryImpl(ReaderFactory readerFactory,
			ObjectReader<UnverifiedMessage> messageReader,
			ObjectReader<SubscriptionUpdate> subscriptionUpdateReader) {
		this.readerFactory = readerFactory;
		this.messageReader = messageReader;
		this.subscriptionUpdateReader = subscriptionUpdateReader;
	}

	public PacketReader createPacketReader(InputStream in) {
		return new PacketReaderImpl(readerFactory, messageReader,
				subscriptionUpdateReader, in);
	}
}
