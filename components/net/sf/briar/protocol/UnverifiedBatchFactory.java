package net.sf.briar.protocol;

import java.util.Collection;

import net.sf.briar.api.protocol.BatchId;
import net.sf.briar.api.protocol.UnverifiedBatch;

interface UnverifiedBatchFactory {

	UnverifiedBatch createUnverifiedBatch(BatchId id,
			Collection<UnverifiedMessage> messages);
}
