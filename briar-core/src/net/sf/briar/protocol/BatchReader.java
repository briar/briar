package net.sf.briar.protocol;

import static net.sf.briar.api.protocol.ProtocolConstants.MAX_PACKET_LENGTH;

import java.io.IOException;
import java.util.List;

import net.sf.briar.api.FormatException;
import net.sf.briar.api.protocol.Types;
import net.sf.briar.api.protocol.UnverifiedBatch;
import net.sf.briar.api.serial.Consumer;
import net.sf.briar.api.serial.CountingConsumer;
import net.sf.briar.api.serial.StructReader;
import net.sf.briar.api.serial.Reader;

class BatchReader implements StructReader<UnverifiedBatch> {

	private final StructReader<UnverifiedMessage> messageReader;
	private final UnverifiedBatchFactory batchFactory;

	BatchReader(StructReader<UnverifiedMessage> messageReader,
			UnverifiedBatchFactory batchFactory) {
		this.messageReader = messageReader;
		this.batchFactory = batchFactory;
	}

	public UnverifiedBatch readStruct(Reader r) throws IOException {
		// Initialise the consumer
		Consumer counting = new CountingConsumer(MAX_PACKET_LENGTH);
		// Read the data
		r.addConsumer(counting);
		r.readStructId(Types.BATCH);
		r.addStructReader(Types.MESSAGE, messageReader);
		List<UnverifiedMessage> messages = r.readList(UnverifiedMessage.class);
		r.removeStructReader(Types.MESSAGE);
		r.removeConsumer(counting);
		if(messages.isEmpty()) throw new FormatException();
		// Build and return the batch
		return batchFactory.createUnverifiedBatch( messages);
	}
}
