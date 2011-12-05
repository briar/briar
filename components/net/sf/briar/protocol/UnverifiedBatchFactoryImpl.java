package net.sf.briar.protocol;

import java.util.Collection;

import net.sf.briar.api.crypto.CryptoComponent;
import net.sf.briar.api.protocol.BatchId;
import net.sf.briar.api.protocol.UnverifiedBatch;

import com.google.inject.Inject;

class UnverifiedBatchFactoryImpl implements UnverifiedBatchFactory {

	private final CryptoComponent crypto;

	@Inject
	UnverifiedBatchFactoryImpl(CryptoComponent crypto) {
		this.crypto = crypto;
	}

	public UnverifiedBatch createUnverifiedBatch(BatchId id,
			Collection<UnverifiedMessage> messages) {
		return new UnverifiedBatchImpl(crypto, id, messages);
	}
}
