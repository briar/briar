package org.briarproject.bramble.record;

import org.briarproject.bramble.api.FormatException;
import org.briarproject.bramble.api.record.Record;
import org.briarproject.bramble.api.record.RecordReader;
import org.briarproject.bramble.test.BrambleTestCase;
import org.briarproject.bramble.util.ByteUtils;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.EOFException;

import static org.briarproject.bramble.api.record.Record.MAX_RECORD_PAYLOAD_BYTES;
import static org.briarproject.bramble.api.record.Record.RECORD_HEADER_BYTES;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

public class RecordReaderImplTest extends BrambleTestCase {

	@Test
	public void testAcceptsEmptyPayload() throws Exception {
		// Version 1, type 2, payload length 0
		byte[] header = new byte[] {1, 2, 0, 0};
		ByteArrayInputStream in = new ByteArrayInputStream(header);
		RecordReader reader = new RecordReaderImpl(in);
		Record record = reader.readRecord();
		assertEquals(1, record.getProtocolVersion());
		assertEquals(2, record.getRecordType());
		assertArrayEquals(new byte[0], record.getPayload());
	}

	@Test
	public void testAcceptsMaxLengthPayload() throws Exception {
		byte[] record =
				new byte[RECORD_HEADER_BYTES + MAX_RECORD_PAYLOAD_BYTES];
		// Version 1, type 2, payload length MAX_RECORD_PAYLOAD_BYTES
		record[0] = 1;
		record[1] = 2;
		ByteUtils.writeUint16(MAX_RECORD_PAYLOAD_BYTES, record, 2);
		ByteArrayInputStream in = new ByteArrayInputStream(record);
		RecordReader reader = new RecordReaderImpl(in);
		reader.readRecord();
	}

	@Test(expected = FormatException.class)
	public void testFormatExceptionIfPayloadLengthIsNegative()
			throws Exception {
		// Version 1, type 2, payload length -1
		byte[] header = new byte[] {1, 2, (byte) 0xFF, (byte) 0xFF};
		ByteArrayInputStream in = new ByteArrayInputStream(header);
		RecordReader reader = new RecordReaderImpl(in);
		reader.readRecord();
	}

	@Test(expected = FormatException.class)
	public void testFormatExceptionIfPayloadLengthIsTooLarge()
			throws Exception {
		// Version 1, type 2, payload length MAX_RECORD_PAYLOAD_BYTES + 1
		byte[] header = new byte[] {1, 2, 0, 0};
		ByteUtils.writeUint16(MAX_RECORD_PAYLOAD_BYTES + 1, header, 2);
		ByteArrayInputStream in = new ByteArrayInputStream(header);
		RecordReader reader = new RecordReaderImpl(in);
		reader.readRecord();
	}

	@Test(expected = EOFException.class)
	public void testEofExceptionIfProtocolVersionIsMissing() throws Exception {
		ByteArrayInputStream in = new ByteArrayInputStream(new byte[0]);
		RecordReader reader = new RecordReaderImpl(in);
		reader.readRecord();
	}

	@Test(expected = EOFException.class)
	public void testEofExceptionIfRecordTypeIsMissing() throws Exception {
		ByteArrayInputStream in = new ByteArrayInputStream(new byte[1]);
		RecordReader reader = new RecordReaderImpl(in);
		reader.readRecord();
	}

	@Test(expected = EOFException.class)
	public void testEofExceptionIfPayloadLengthIsMissing() throws Exception {
		ByteArrayInputStream in = new ByteArrayInputStream(new byte[2]);
		RecordReader reader = new RecordReaderImpl(in);
		reader.readRecord();
	}

	@Test(expected = EOFException.class)
	public void testEofExceptionIfPayloadLengthIsTruncated() throws Exception {
		ByteArrayInputStream in = new ByteArrayInputStream(new byte[3]);
		RecordReader reader = new RecordReaderImpl(in);
		reader.readRecord();
	}

	@Test(expected = EOFException.class)
	public void testEofExceptionIfPayloadIsTruncated() throws Exception {
		// Version 0, type 0, payload length 1
		byte[] header = new byte[] {0, 0, 0, 1};
		ByteArrayInputStream in = new ByteArrayInputStream(header);
		RecordReader reader = new RecordReaderImpl(in);
		reader.readRecord();
	}
}
