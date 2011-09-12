package net.sf.briar.protocol;

import java.io.IOException;
import java.security.MessageDigest;
import java.util.List;

import net.sf.briar.api.crypto.CryptoComponent;
import net.sf.briar.api.protocol.Batch;
import net.sf.briar.api.protocol.BatchId;
import net.sf.briar.api.protocol.Message;
import net.sf.briar.api.protocol.ProtocolConstants;
import net.sf.briar.api.protocol.Types;
import net.sf.briar.api.serial.Consumer;
import net.sf.briar.api.serial.ObjectReader;
import net.sf.briar.api.serial.Reader;

class BatchReader implements ObjectReader<Batch> {

	private final MessageDigest messageDigest;
	private final ObjectReader<Message> messageReader;
	private final BatchFactory batchFactory;

	BatchReader(CryptoComponent crypto, ObjectReader<Message> messageReader,
			BatchFactory batchFactory) {
		messageDigest = crypto.getMessageDigest();
		this.messageReader = messageReader;
		this.batchFactory = batchFactory;
	}

	public Batch readObject(Reader r) throws IOException {
		// Initialise the consumers
		Consumer counting =
			new CountingConsumer(ProtocolConstants.MAX_PACKET_LENGTH);
		DigestingConsumer digesting = new DigestingConsumer(messageDigest);
		messageDigest.reset();
		// Read and digest the data
		r.addConsumer(counting);
		r.addConsumer(digesting);
		r.readUserDefinedId(Types.BATCH);
		r.addObjectReader(Types.MESSAGE, messageReader);
		List<Message> messages = r.readList(Message.class);
		r.removeObjectReader(Types.MESSAGE);
		r.removeConsumer(digesting);
		r.removeConsumer(counting);
		// Build and return the batch
		BatchId id = new BatchId(messageDigest.digest());
		return batchFactory.createBatch(id, messages);
	}
}
