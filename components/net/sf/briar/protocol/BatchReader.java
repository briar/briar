package net.sf.briar.protocol;

import static net.sf.briar.api.protocol.ProtocolConstants.MAX_PACKET_LENGTH;

import java.io.IOException;
import java.util.List;

import net.sf.briar.api.FormatException;
import net.sf.briar.api.protocol.Types;
import net.sf.briar.api.protocol.UnverifiedBatch;
import net.sf.briar.api.serial.Consumer;
import net.sf.briar.api.serial.CountingConsumer;
import net.sf.briar.api.serial.ObjectReader;
import net.sf.briar.api.serial.Reader;

class BatchReader implements ObjectReader<UnverifiedBatch> {

	private final ObjectReader<UnverifiedMessage> messageReader;
	private final UnverifiedBatchFactory batchFactory;

	BatchReader(ObjectReader<UnverifiedMessage> messageReader,
			UnverifiedBatchFactory batchFactory) {
		this.messageReader = messageReader;
		this.batchFactory = batchFactory;
	}

	public UnverifiedBatch readObject(Reader r) throws IOException {
		// Initialise the consumer
		Consumer counting = new CountingConsumer(MAX_PACKET_LENGTH);
		// Read the data
		r.addConsumer(counting);
		r.readStructId(Types.BATCH);
		r.addObjectReader(Types.MESSAGE, messageReader);
		List<UnverifiedMessage> messages = r.readList(UnverifiedMessage.class);
		r.removeObjectReader(Types.MESSAGE);
		r.removeConsumer(counting);
		if(messages.isEmpty()) throw new FormatException();
		// Build and return the batch
		return batchFactory.createUnverifiedBatch( messages);
	}
}
