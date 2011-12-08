package net.sf.briar.protocol;

import java.util.Collection;

import net.sf.briar.api.protocol.UnverifiedBatch;

interface UnverifiedBatchFactory {

	UnverifiedBatch createUnverifiedBatch(
			Collection<UnverifiedMessage> messages);
}
