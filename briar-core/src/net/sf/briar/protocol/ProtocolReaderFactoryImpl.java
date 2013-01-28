package net.sf.briar.protocol;

import java.io.InputStream;

import net.sf.briar.api.protocol.ProtocolReader;
import net.sf.briar.api.protocol.ProtocolReaderFactory;
import net.sf.briar.api.protocol.SubscriptionUpdate;
import net.sf.briar.api.protocol.UnverifiedMessage;
import net.sf.briar.api.serial.ReaderFactory;
import net.sf.briar.api.serial.StructReader;

import com.google.inject.Inject;
import com.google.inject.Provider;

// FIXME: See whether these providers can be got rid of
class ProtocolReaderFactoryImpl implements ProtocolReaderFactory {

	private final ReaderFactory readerFactory;
	private final Provider<StructReader<UnverifiedMessage>> messageProvider;
	private final Provider<StructReader<SubscriptionUpdate>> subscriptionUpdateProvider;

	@Inject
	ProtocolReaderFactoryImpl(ReaderFactory readerFactory,
			Provider<StructReader<UnverifiedMessage>> messageProvider,
			Provider<StructReader<SubscriptionUpdate>> subscriptionUpdateProvider) {
		this.readerFactory = readerFactory;
		this.messageProvider = messageProvider;
		this.subscriptionUpdateProvider = subscriptionUpdateProvider;
	}

	public ProtocolReader createProtocolReader(InputStream in) {
		return new ProtocolReaderImpl(readerFactory, messageProvider.get(),
				subscriptionUpdateProvider.get(), in);
	}
}
