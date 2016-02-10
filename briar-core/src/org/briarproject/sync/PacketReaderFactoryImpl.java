package org.briarproject.sync;

import org.briarproject.api.crypto.CryptoComponent;
import org.briarproject.api.sync.PacketReader;
import org.briarproject.api.sync.PacketReaderFactory;

import java.io.InputStream;

import javax.inject.Inject;

class PacketReaderFactoryImpl implements PacketReaderFactory {

	private final CryptoComponent crypto;

	@Inject
	PacketReaderFactoryImpl(CryptoComponent crypto) {
		this.crypto = crypto;
	}

	public PacketReader createPacketReader(InputStream in) {
		return new PacketReaderImpl(crypto, in);
	}
}
