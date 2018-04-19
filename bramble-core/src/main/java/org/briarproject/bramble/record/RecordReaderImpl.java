package org.briarproject.bramble.record;

import org.briarproject.bramble.api.FormatException;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.record.Record;
import org.briarproject.bramble.api.record.RecordReader;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;

import javax.annotation.concurrent.NotThreadSafe;

import static org.briarproject.bramble.api.record.Record.MAX_RECORD_PAYLOAD_BYTES;

@NotThreadSafe
@NotNullByDefault
class RecordReaderImpl implements RecordReader {

	private final DataInputStream in;

	RecordReaderImpl(InputStream in) {
		this.in = new DataInputStream(new BufferedInputStream(in, 1024));
	}

	@Override
	public Record readRecord() throws IOException {
		byte protocolVersion = in.readByte();
		byte recordType = in.readByte();
		int payloadLength = in.readShort() & 0xFFFF; // Convert to unsigned
		if (payloadLength < 0 || payloadLength > MAX_RECORD_PAYLOAD_BYTES)
			throw new FormatException();
		byte[] payload = new byte[payloadLength];
		in.readFully(payload);
		return new Record(protocolVersion, recordType, payload);
	}

	@Override
	public void close() throws IOException {
		in.close();
	}
}
