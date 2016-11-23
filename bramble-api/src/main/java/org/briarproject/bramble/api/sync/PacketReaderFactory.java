package org.briarproject.bramble.api.sync;

import org.briarproject.bramble.api.nullsafety.NotNullByDefault;

import java.io.InputStream;

@NotNullByDefault
public interface PacketReaderFactory {

	PacketReader createPacketReader(InputStream in);
}
