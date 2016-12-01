package org.briarproject.bramble.sync;

import org.briarproject.bramble.api.crypto.CryptoComponent;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.sync.PacketReader;
import org.briarproject.bramble.api.sync.PacketReaderFactory;

import java.io.InputStream;

import javax.annotation.concurrent.Immutable;
import javax.inject.Inject;

@Immutable
@NotNullByDefault
class PacketReaderFactoryImpl implements PacketReaderFactory {

	private final CryptoComponent crypto;

	@Inject
	PacketReaderFactoryImpl(CryptoComponent crypto) {
		this.crypto = crypto;
	}

	@Override
	public PacketReader createPacketReader(InputStream in) {
		return new PacketReaderImpl(crypto, in);
	}
}
