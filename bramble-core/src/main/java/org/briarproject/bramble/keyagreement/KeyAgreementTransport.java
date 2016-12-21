package org.briarproject.bramble.keyagreement;

import org.briarproject.bramble.api.keyagreement.KeyAgreementConnection;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.plugin.TransportId;
import org.briarproject.bramble.api.plugin.duplex.DuplexTransportConnection;
import org.briarproject.bramble.util.ByteUtils;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.logging.Logger;

import static java.util.logging.Level.WARNING;
import static org.briarproject.bramble.api.keyagreement.KeyAgreementConstants.PROTOCOL_VERSION;
import static org.briarproject.bramble.api.keyagreement.KeyAgreementConstants.RECORD_HEADER_LENGTH;
import static org.briarproject.bramble.api.keyagreement.KeyAgreementConstants.RECORD_HEADER_PAYLOAD_LENGTH_OFFSET;
import static org.briarproject.bramble.api.keyagreement.RecordTypes.ABORT;
import static org.briarproject.bramble.api.keyagreement.RecordTypes.CONFIRM;
import static org.briarproject.bramble.api.keyagreement.RecordTypes.KEY;

/**
 * Handles the sending and receiving of BQP records.
 */
@NotNullByDefault
class KeyAgreementTransport {

	private static final Logger LOG =
			Logger.getLogger(KeyAgreementTransport.class.getName());

	private final KeyAgreementConnection kac;
	private final InputStream in;
	private final OutputStream out;

	KeyAgreementTransport(KeyAgreementConnection kac)
			throws IOException {
		this.kac = kac;
		in = kac.getConnection().getReader().getInputStream();
		out = kac.getConnection().getWriter().getOutputStream();
	}

	public DuplexTransportConnection getConnection() {
		return kac.getConnection();
	}

	public TransportId getTransportId() {
		return kac.getTransportId();
	}

	void sendKey(byte[] key) throws IOException {
		writeRecord(KEY, key);
	}

	byte[] receiveKey() throws AbortException {
		return readRecord(KEY);
	}

	void sendConfirm(byte[] confirm) throws IOException {
		writeRecord(CONFIRM, confirm);
	}

	byte[] receiveConfirm() throws AbortException {
		return readRecord(CONFIRM);
	}

	void sendAbort(boolean exception) {
		try {
			writeRecord(ABORT, new byte[0]);
		} catch (IOException e) {
			if (LOG.isLoggable(WARNING)) LOG.log(WARNING, e.toString(), e);
			exception = true;
		}
		tryToClose(exception);
	}

	public void tryToClose(boolean exception) {
		try {
			LOG.info("Closing connection");
			kac.getConnection().getReader().dispose(exception, true);
			kac.getConnection().getWriter().dispose(exception);
		} catch (IOException e) {
			if (LOG.isLoggable(WARNING)) LOG.log(WARNING, e.toString(), e);
		}
	}

	private void writeRecord(byte type, byte[] payload) throws IOException {
		byte[] recordHeader = new byte[RECORD_HEADER_LENGTH];
		recordHeader[0] = PROTOCOL_VERSION;
		recordHeader[1] = type;
		ByteUtils.writeUint16(payload.length, recordHeader,
				RECORD_HEADER_PAYLOAD_LENGTH_OFFSET);
		out.write(recordHeader);
		out.write(payload);
		out.flush();
	}

	private byte[] readRecord(byte expectedType) throws AbortException {
		while (true) {
			byte[] header = readHeader();
			byte version = header[0], type = header[1];
			int len = ByteUtils.readUint16(header,
					RECORD_HEADER_PAYLOAD_LENGTH_OFFSET);
			// Reject unrecognised protocol version
			if (version != PROTOCOL_VERSION) throw new AbortException(false);
			if (type == ABORT) throw new AbortException(true);
			if (type == expectedType) {
				try {
					return readData(len);
				} catch (IOException e) {
					throw new AbortException(e);
				}
			}
			// Reject recognised but unexpected record type
			if (type == KEY || type == CONFIRM) throw new AbortException(false);
			// Skip unrecognised record type
			try {
				readData(len);
			} catch (IOException e) {
				throw new AbortException(e);
			}
		}
	}

	private byte[] readHeader() throws AbortException {
		try {
			return readData(RECORD_HEADER_LENGTH);
		} catch (IOException e) {
			throw new AbortException(e);
		}
	}

	private byte[] readData(int len) throws IOException {
		byte[] data = new byte[len];
		int offset = 0;
		while (offset < data.length) {
			int read = in.read(data, offset, data.length - offset);
			if (read == -1) throw new EOFException();
			offset += read;
		}
		return data;
	}
}
