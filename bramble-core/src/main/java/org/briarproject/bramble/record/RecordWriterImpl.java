package org.briarproject.bramble.record;

import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.record.Record;
import org.briarproject.bramble.api.record.RecordWriter;

import java.io.BufferedOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;

import javax.annotation.concurrent.NotThreadSafe;

@NotThreadSafe
@NotNullByDefault
class RecordWriterImpl implements RecordWriter {

	private final DataOutputStream out;

	RecordWriterImpl(OutputStream out) {
		this.out = new DataOutputStream(new BufferedOutputStream(out, 1024));
	}

	@Override
	public void writeRecord(Record r) throws IOException {
		out.write(r.getProtocolVersion());
		out.write(r.getRecordType());
		byte[] payload = r.getPayload();
		out.writeShort(payload.length);
		out.write(payload);
	}

	@Override
	public void flush() throws IOException {
		out.flush();
	}

	@Override
	public void close() throws IOException {
		out.close();
	}
}
