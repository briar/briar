package org.briarproject.bramble.record;

import org.briarproject.bramble.api.FormatException;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.record.Record;
import org.briarproject.bramble.api.record.RecordReader;
import org.briarproject.bramble.util.ByteUtils;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;

import javax.annotation.concurrent.NotThreadSafe;

import static org.briarproject.bramble.api.record.Record.MAX_RECORD_PAYLOAD_BYTES;
import static org.briarproject.bramble.api.record.Record.RECORD_HEADER_BYTES;

@NotThreadSafe
@NotNullByDefault
class RecordReaderImpl implements RecordReader {

	private final DataInputStream in;
	private final byte[] header = new byte[RECORD_HEADER_BYTES];

	RecordReaderImpl(InputStream in) {
		this.in = new DataInputStream(in);
	}

	@Override
	public Record readRecord() throws IOException {
		in.readFully(header);
		byte protocolVersion = header[0];
		byte recordType = header[1];
		int payloadLength = ByteUtils.readUint16(header, 2);
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
