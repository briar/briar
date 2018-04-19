package org.briarproject.bramble.sync;

import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.sync.SyncRecordWriter;
import org.briarproject.bramble.api.sync.SyncRecordWriterFactory;

import java.io.OutputStream;

@NotNullByDefault
class SyncRecordWriterFactoryImpl implements SyncRecordWriterFactory {

	@Override
	public SyncRecordWriter createRecordWriter(OutputStream out) {
		return new SyncRecordWriterImpl(out);
	}
}
