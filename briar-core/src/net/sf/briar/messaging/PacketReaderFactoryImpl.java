package net.sf.briar.messaging;

import java.io.InputStream;

import net.sf.briar.api.messaging.PacketReader;
import net.sf.briar.api.messaging.PacketReaderFactory;
import net.sf.briar.api.messaging.SubscriptionUpdate;
import net.sf.briar.api.messaging.UnverifiedMessage;
import net.sf.briar.api.serial.ReaderFactory;
import net.sf.briar.api.serial.StructReader;

import com.google.inject.Inject;

class PacketReaderFactoryImpl implements PacketReaderFactory {

	private final ReaderFactory readerFactory;
	private final StructReader<UnverifiedMessage> messageReader;
	private final StructReader<SubscriptionUpdate> subscriptionUpdateReader;

	@Inject
	PacketReaderFactoryImpl(ReaderFactory readerFactory,
			StructReader<UnverifiedMessage> messageReader,
			StructReader<SubscriptionUpdate> subscriptionUpdateReader) {
		this.readerFactory = readerFactory;
		this.messageReader = messageReader;
		this.subscriptionUpdateReader = subscriptionUpdateReader;
	}

	public PacketReader createPacketReader(InputStream in) {
		return new PacketReaderImpl(readerFactory, messageReader,
				subscriptionUpdateReader, in);
	}
}
