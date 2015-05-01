package org.briarproject.messaging;

import java.io.InputStream;

import javax.inject.Inject;

import org.briarproject.api.messaging.PacketReader;
import org.briarproject.api.messaging.PacketReaderFactory;
import org.briarproject.api.messaging.SubscriptionUpdate;
import org.briarproject.api.messaging.UnverifiedMessage;
import org.briarproject.api.serial.ReaderFactory;
import org.briarproject.api.serial.ObjectReader;

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
