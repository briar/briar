package org.briarproject.bramble.sync;

import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.sync.MessageFactory;
import org.briarproject.bramble.api.sync.RecordReader;
import org.briarproject.bramble.api.sync.RecordReaderFactory;

import java.io.InputStream;

import javax.annotation.concurrent.Immutable;
import javax.inject.Inject;

@Immutable
@NotNullByDefault
class RecordReaderFactoryImpl implements RecordReaderFactory {

	private final MessageFactory messageFactory;

	@Inject
	RecordReaderFactoryImpl(MessageFactory messageFactory) {
		this.messageFactory = messageFactory;
	}

	@Override
	public RecordReader createRecordReader(InputStream in) {
		return new RecordReaderImpl(messageFactory, in);
	}
}
