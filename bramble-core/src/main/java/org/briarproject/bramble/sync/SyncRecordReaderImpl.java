package org.briarproject.bramble.sync;

import org.briarproject.bramble.api.FormatException;
import org.briarproject.bramble.api.UniqueId;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.record.Record;
import org.briarproject.bramble.api.record.RecordReader;
import org.briarproject.bramble.api.sync.Ack;
import org.briarproject.bramble.api.sync.Message;
import org.briarproject.bramble.api.sync.MessageFactory;
import org.briarproject.bramble.api.sync.MessageId;
import org.briarproject.bramble.api.sync.Offer;
import org.briarproject.bramble.api.sync.Request;
import org.briarproject.bramble.api.sync.SyncRecordReader;
import org.briarproject.bramble.util.ByteUtils;

import java.io.EOFException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nullable;
import javax.annotation.concurrent.NotThreadSafe;

import static org.briarproject.bramble.api.sync.RecordTypes.ACK;
import static org.briarproject.bramble.api.sync.RecordTypes.MESSAGE;
import static org.briarproject.bramble.api.sync.RecordTypes.OFFER;
import static org.briarproject.bramble.api.sync.RecordTypes.REQUEST;
import static org.briarproject.bramble.api.sync.SyncConstants.MESSAGE_HEADER_LENGTH;
import static org.briarproject.bramble.api.sync.SyncConstants.PROTOCOL_VERSION;

@NotThreadSafe
@NotNullByDefault
class SyncRecordReaderImpl implements SyncRecordReader {

	private final MessageFactory messageFactory;
	private final RecordReader reader;

	@Nullable
	private Record nextRecord = null;
	private boolean eof = false;

	SyncRecordReaderImpl(MessageFactory messageFactory, RecordReader reader) {
		this.messageFactory = messageFactory;
		this.reader = reader;
	}

	private void readRecord() throws IOException {
		assert nextRecord == null;
		while (true) {
			nextRecord = reader.readRecord();
			// Check the protocol version
			byte version = nextRecord.getProtocolVersion();
			if (version != PROTOCOL_VERSION) throw new FormatException();
			byte type = nextRecord.getRecordType();
			// Return if this is a known record type, otherwise continue
			if (type == ACK || type == MESSAGE || type == OFFER ||
					type == REQUEST) {
				return;
			}
		}
	}

	private byte getNextRecordType() {
		assert nextRecord != null;
		return nextRecord.getRecordType();
	}

	/**
	 * Returns true if there's another record available or false if we've
	 * reached the end of the input stream.
	 * <p>
	 * If a record is available, it's been read into the buffer by the time
	 * eof() returns, so the method that called eof() can access the record
	 * from the buffer, for example to check its type or extract its payload.
	 */
	@Override
	public boolean eof() throws IOException {
		if (nextRecord != null) return false;
		if (eof) return true;
		try {
			readRecord();
			return false;
		} catch (EOFException e) {
			nextRecord = null;
			eof = true;
			return true;
		}
	}

	@Override
	public boolean hasAck() throws IOException {
		return !eof() && getNextRecordType() == ACK;
	}

	@Override
	public Ack readAck() throws IOException {
		if (!hasAck()) throw new FormatException();
		return new Ack(readMessageIds());
	}

	private List<MessageId> readMessageIds() throws IOException {
		assert nextRecord != null;
		byte[] payload = nextRecord.getPayload();
		if (payload.length == 0) throw new FormatException();
		if (payload.length % UniqueId.LENGTH != 0) throw new FormatException();
		List<MessageId> ids = new ArrayList<>(payload.length / UniqueId.LENGTH);
		for (int off = 0; off < payload.length; off += UniqueId.LENGTH) {
			byte[] id = new byte[UniqueId.LENGTH];
			System.arraycopy(payload, off, id, 0, UniqueId.LENGTH);
			ids.add(new MessageId(id));
		}
		nextRecord = null;
		return ids;
	}

	@Override
	public boolean hasMessage() throws IOException {
		return !eof() && getNextRecordType() == MESSAGE;
	}

	@Override
	public Message readMessage() throws IOException {
		if (!hasMessage()) throw new FormatException();
		assert nextRecord != null;
		byte[] payload = nextRecord.getPayload();
		if (payload.length < MESSAGE_HEADER_LENGTH) throw new FormatException();
		// Validate timestamp
		long timestamp = ByteUtils.readUint64(payload, UniqueId.LENGTH);
		if (timestamp < 0) throw new FormatException();
		nextRecord = null;
		return messageFactory.createMessage(payload);
	}

	@Override
	public boolean hasOffer() throws IOException {
		return !eof() && getNextRecordType() == OFFER;
	}

	@Override
	public Offer readOffer() throws IOException {
		if (!hasOffer()) throw new FormatException();
		return new Offer(readMessageIds());
	}

	@Override
	public boolean hasRequest() throws IOException {
		return !eof() && getNextRecordType() == REQUEST;
	}

	@Override
	public Request readRequest() throws IOException {
		if (!hasRequest()) throw new FormatException();
		return new Request(readMessageIds());
	}

}
