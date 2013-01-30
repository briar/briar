package net.sf.briar.protocol;

import java.io.InputStream;

import net.sf.briar.api.protocol.ProtocolReader;
import net.sf.briar.api.protocol.ProtocolReaderFactory;
import net.sf.briar.api.protocol.SubscriptionUpdate;
import net.sf.briar.api.protocol.UnverifiedMessage;
import net.sf.briar.api.serial.ReaderFactory;
import net.sf.briar.api.serial.StructReader;

import com.google.inject.Inject;

class ProtocolReaderFactoryImpl implements ProtocolReaderFactory {

	private final ReaderFactory readerFactory;
	private final StructReader<UnverifiedMessage> messageReader;
	private final StructReader<SubscriptionUpdate> subscriptionUpdateReader;

	@Inject
	ProtocolReaderFactoryImpl(ReaderFactory readerFactory,
			StructReader<UnverifiedMessage> messageReader,
			StructReader<SubscriptionUpdate> subscriptionUpdateReader) {
		this.readerFactory = readerFactory;
		this.messageReader = messageReader;
		this.subscriptionUpdateReader = subscriptionUpdateReader;
	}

	public ProtocolReader createProtocolReader(InputStream in) {
		return new ProtocolReaderImpl(readerFactory, messageReader,
				subscriptionUpdateReader, in);
	}
}
