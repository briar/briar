package org.briarproject.bramble.api.sync;

import org.briarproject.bramble.api.nullsafety.NotNullByDefault;

import java.io.InputStream;

@NotNullByDefault
public interface RecordReaderFactory {

	RecordReader createRecordReader(InputStream in);
}
