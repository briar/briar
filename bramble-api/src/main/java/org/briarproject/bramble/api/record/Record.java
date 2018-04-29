package org.briarproject.bramble.api.record;

import org.briarproject.bramble.api.nullsafety.NotNullByDefault;

import javax.annotation.concurrent.Immutable;

@Immutable
@NotNullByDefault
public class Record {

	public static final int RECORD_HEADER_BYTES = 4;
	public static final int MAX_RECORD_PAYLOAD_BYTES = 48 * 1024; // 48 KiB

	private final byte protocolVersion, recordType;
	private final byte[] payload;

	public Record(byte protocolVersion, byte recordType, byte[] payload) {
		if (payload.length > MAX_RECORD_PAYLOAD_BYTES)
			throw new IllegalArgumentException();
		this.protocolVersion = protocolVersion;
		this.recordType = recordType;
		this.payload = payload;
	}

	public byte getProtocolVersion() {
		return protocolVersion;
	}

	public byte getRecordType() {
		return recordType;
	}

	public byte[] getPayload() {
		return payload;
	}
}
