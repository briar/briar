package org.briarproject.bramble.record;

import org.briarproject.bramble.api.FormatException;
import org.briarproject.bramble.api.Predicate;
import org.briarproject.bramble.api.record.Record;
import org.briarproject.bramble.api.record.RecordReader;
import org.briarproject.bramble.test.BrambleTestCase;
import org.briarproject.bramble.util.ByteUtils;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.EOFException;

import static org.briarproject.bramble.api.record.Record.MAX_RECORD_PAYLOAD_BYTES;
import static org.briarproject.bramble.api.record.Record.RECORD_HEADER_BYTES;
import static org.briarproject.bramble.test.TestUtils.getRandomBytes;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.fail;

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

	@Test
	public void testAcceptsAndRejectsRecords() throws Exception {
		// Version 0, type 0, payload length 123
		byte[] header1 = new byte[] {0, 0, 0, 123};
		// Version 0, type 1, payload length 123
		byte[] header2  = new byte[] {0, 1, 0, 123};
		// Version 1, type 0, payload length 123
		byte[] header3 = new byte[] {1, 0, 0, 123};
		// Same payload for all records
		byte[] payload = getRandomBytes(123);

		ByteArrayOutputStream out = new ByteArrayOutputStream();
		out.write(header1);
		out.write(payload);
		out.write(header2);
		out.write(payload);
		out.write(header3);
		out.write(payload);
		ByteArrayInputStream in = new ByteArrayInputStream(out.toByteArray());
		RecordReader reader = new RecordReaderImpl(in);

		// Accept records with version 0, type 0 or 1
		Predicate<Record> accept = r -> {
			byte version = r.getProtocolVersion(), type = r.getRecordType();
			return version == 0 && (type == 0 || type == 1);
		};
		// Ignore records with version 0, any other type
		Predicate<Record> ignore = r -> {
			byte version = r.getProtocolVersion(), type = r.getRecordType();
			return version == 0 && !(type == 0 || type == 1);
		};

		// The first record should be accepted
		Record r = reader.readRecord(accept, ignore);
		assertNotNull(r);
		assertEquals(0, r.getProtocolVersion());
		assertEquals(0, r.getRecordType());
		assertArrayEquals(payload, r.getPayload());

		// The second record should be accepted
		r = reader.readRecord(accept, ignore);
		assertNotNull(r);
		assertEquals(0, r.getProtocolVersion());
		assertEquals(1, r.getRecordType());
		assertArrayEquals(payload, r.getPayload());

		// The third record should be rejected
		try {
			reader.readRecord(accept, ignore);
			fail();
		} catch (FormatException expected) {
			// Expected
		}
	}

	@Test
	public void testAcceptsAndIgnoresRecords() throws Exception {
		// Version 0, type 0, payload length 123
		byte[] header1 = new byte[] {0, 0, 0, 123};
		// Version 0, type 2, payload length 123
		byte[] header2  = new byte[] {0, 2, 0, 123};
		// Version 0, type 1, payload length 123
		byte[] header3 = new byte[] {0, 1, 0, 123};
		// Same payload for all records
		byte[] payload = getRandomBytes(123);

		ByteArrayOutputStream out = new ByteArrayOutputStream();
		out.write(header1);
		out.write(payload);
		out.write(header2);
		out.write(payload);
		out.write(header3);
		out.write(payload);
		ByteArrayInputStream in = new ByteArrayInputStream(out.toByteArray());
		RecordReader reader = new RecordReaderImpl(in);

		// Accept records with version 0, type 0 or 1
		Predicate<Record> accept = r -> {
			byte version = r.getProtocolVersion(), type = r.getRecordType();
			return version == 0 && (type == 0 || type == 1);
		};
		// Ignore records with version 0, any other type
		Predicate<Record> ignore = r -> {
			byte version = r.getProtocolVersion(), type = r.getRecordType();
			return version == 0 && !(type == 0 || type == 1);
		};

		// The first record should be accepted
		Record r = reader.readRecord(accept, ignore);
		assertNotNull(r);
		assertEquals(0, r.getProtocolVersion());
		assertEquals(0, r.getRecordType());
		assertArrayEquals(payload, r.getPayload());

		// The second record should be ignored, the third should be accepted
		r = reader.readRecord(accept, ignore);
		assertNotNull(r);
		assertEquals(0, r.getProtocolVersion());
		assertEquals(1, r.getRecordType());
		assertArrayEquals(payload, r.getPayload());

		// The reader should have reached the end of the stream
		assertNull(reader.readRecord(accept, ignore));
	}
}
