package org.briarproject.bramble.sync;

import org.briarproject.bramble.api.FormatException;
import org.briarproject.bramble.api.UniqueId;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.sync.Ack;
import org.briarproject.bramble.api.sync.GroupId;
import org.briarproject.bramble.api.sync.Message;
import org.briarproject.bramble.api.sync.MessageFactory;
import org.briarproject.bramble.api.sync.MessageId;
import org.briarproject.bramble.api.sync.Offer;
import org.briarproject.bramble.api.sync.RecordReader;
import org.briarproject.bramble.api.sync.Request;
import org.briarproject.bramble.util.ByteUtils;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import javax.annotation.concurrent.NotThreadSafe;

import static org.briarproject.bramble.api.sync.RecordTypes.ACK;
import static org.briarproject.bramble.api.sync.RecordTypes.MESSAGE;
import static org.briarproject.bramble.api.sync.RecordTypes.OFFER;
import static org.briarproject.bramble.api.sync.RecordTypes.REQUEST;
import static org.briarproject.bramble.api.sync.SyncConstants.MAX_RECORD_PAYLOAD_LENGTH;
import static org.briarproject.bramble.api.sync.SyncConstants.MESSAGE_HEADER_LENGTH;
import static org.briarproject.bramble.api.sync.SyncConstants.PROTOCOL_VERSION;
import static org.briarproject.bramble.api.sync.SyncConstants.RECORD_HEADER_LENGTH;

@NotThreadSafe
@NotNullByDefault
class RecordReaderImpl implements RecordReader {

	private enum State {BUFFER_EMPTY, BUFFER_FULL, EOF}

	private final MessageFactory messageFactory;
	private final InputStream in;
	private final byte[] header, payload;

	private State state = State.BUFFER_EMPTY;
	private int payloadLength = 0;

	RecordReaderImpl(MessageFactory messageFactory, InputStream in) {
		this.messageFactory = messageFactory;
		this.in = in;
		header = new byte[RECORD_HEADER_LENGTH];
		payload = new byte[MAX_RECORD_PAYLOAD_LENGTH];
	}

	private void readRecord() throws IOException {
		if (state != State.BUFFER_EMPTY) throw new IllegalStateException();
		while (true) {
			// Read the header
			int offset = 0;
			while (offset < RECORD_HEADER_LENGTH) {
				int read =
						in.read(header, offset, RECORD_HEADER_LENGTH - offset);
				if (read == -1) {
					if (offset > 0) throw new FormatException();
					state = State.EOF;
					return;
				}
				offset += read;
			}
			byte version = header[0], type = header[1];
			payloadLength = ByteUtils.readUint16(header, 2);
			// Check the protocol version
			if (version != PROTOCOL_VERSION) throw new FormatException();
			// Check the payload length
			if (payloadLength > MAX_RECORD_PAYLOAD_LENGTH)
				throw new FormatException();
			// Read the payload
			offset = 0;
			while (offset < payloadLength) {
				int read = in.read(payload, offset, payloadLength - offset);
				if (read == -1) throw new FormatException();
				offset += read;
			}
			state = State.BUFFER_FULL;
			// Return if this is a known record type, otherwise continue
			if (type == ACK || type == MESSAGE || type == OFFER ||
					type == REQUEST) {
				return;
			}
		}
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
		if (state == State.BUFFER_EMPTY) readRecord();
		if (state == State.BUFFER_EMPTY) throw new IllegalStateException();
		return state == State.EOF;
	}

	@Override
	public boolean hasAck() throws IOException {
		return !eof() && header[1] == ACK;
	}

	@Override
	public Ack readAck() throws IOException {
		if (!hasAck()) throw new FormatException();
		return new Ack(readMessageIds());
	}

	private List<MessageId> readMessageIds() throws IOException {
		if (payloadLength == 0) throw new FormatException();
		if (payloadLength % UniqueId.LENGTH != 0) throw new FormatException();
		List<MessageId> ids = new ArrayList<MessageId>();
		for (int off = 0; off < payloadLength; off += UniqueId.LENGTH) {
			byte[] id = new byte[UniqueId.LENGTH];
			System.arraycopy(payload, off, id, 0, UniqueId.LENGTH);
			ids.add(new MessageId(id));
		}
		state = State.BUFFER_EMPTY;
		return ids;
	}

	@Override
	public boolean hasMessage() throws IOException {
		return !eof() && header[1] == MESSAGE;
	}

	@Override
	public Message readMessage() throws IOException {
		if (!hasMessage()) throw new FormatException();
		if (payloadLength <= MESSAGE_HEADER_LENGTH) throw new FormatException();
		// Group ID
		byte[] id = new byte[UniqueId.LENGTH];
		System.arraycopy(payload, 0, id, 0, UniqueId.LENGTH);
		GroupId groupId = new GroupId(id);
		// Timestamp
		long timestamp = ByteUtils.readUint64(payload, UniqueId.LENGTH);
		if (timestamp < 0) throw new FormatException();
		// Body
		byte[] body = new byte[payloadLength - MESSAGE_HEADER_LENGTH];
		System.arraycopy(payload, MESSAGE_HEADER_LENGTH, body, 0,
				payloadLength - MESSAGE_HEADER_LENGTH);
		state = State.BUFFER_EMPTY;
		return messageFactory.createMessage(groupId, timestamp, body);
	}

	@Override
	public boolean hasOffer() throws IOException {
		return !eof() && header[1] == OFFER;
	}

	@Override
	public Offer readOffer() throws IOException {
		if (!hasOffer()) throw new FormatException();
		return new Offer(readMessageIds());
	}

	@Override
	public boolean hasRequest() throws IOException {
		return !eof() && header[1] == REQUEST;
	}

	@Override
	public Request readRequest() throws IOException {
		if (!hasRequest()) throw new FormatException();
		return new Request(readMessageIds());
	}

}
