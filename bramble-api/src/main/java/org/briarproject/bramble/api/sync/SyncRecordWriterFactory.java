package org.briarproject.bramble.api.sync;

import org.briarproject.bramble.api.nullsafety.NotNullByDefault;

import java.io.OutputStream;

@NotNullByDefault
public interface SyncRecordWriterFactory {

	SyncRecordWriter createRecordWriter(OutputStream out);
}
