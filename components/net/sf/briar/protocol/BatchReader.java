package net.sf.briar.protocol;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.PublicKey;
import java.security.Signature;
import java.security.SignatureException;
import java.util.List;

import net.sf.briar.api.protocol.Batch;
import net.sf.briar.api.protocol.BatchId;
import net.sf.briar.api.protocol.Message;
import net.sf.briar.api.protocol.Tags;
import net.sf.briar.api.serial.ObjectReader;
import net.sf.briar.api.serial.Reader;

public class BatchReader implements ObjectReader<Batch> {

	private final PublicKey publicKey;
	private final Signature signature;
	private final MessageDigest messageDigest;
	private final ObjectReader<Message> messageReader;
	private final BatchFactory batchFactory;

	BatchReader(PublicKey publicKey, Signature signature,
			MessageDigest messageDigest, ObjectReader<Message> messageReader,
			BatchFactory batchFactory) {
		this.publicKey = publicKey;
		this.signature = signature;
		this.messageDigest = messageDigest;
		this.messageReader = messageReader;
		this.batchFactory = batchFactory;
	}

	public Batch readObject(Reader reader) throws IOException,
	GeneralSecurityException {
		// Initialise the input stream
		CountingConsumer counting = new CountingConsumer(Batch.MAX_SIZE);
		DigestingConsumer digesting = new DigestingConsumer(messageDigest);
		messageDigest.reset();
		SigningConsumer signing = new SigningConsumer(signature);
		signature.initVerify(publicKey);
		// Read the signed data
		reader.addConsumer(counting);
		reader.addConsumer(digesting);
		reader.addConsumer(signing);
		reader.addObjectReader(Tags.MESSAGE, messageReader);
		List<Message> messages = reader.readList(Message.class);
		reader.removeObjectReader(Tags.MESSAGE);
		reader.removeConsumer(signing);
		// Read and verify the signature
		byte[] sig = reader.readRaw();
		reader.removeConsumer(digesting);
		reader.removeConsumer(counting);
		if(!signature.verify(sig)) throw new SignatureException();
		// Build and return the batch
		BatchId id = new BatchId(messageDigest.digest());
		return batchFactory.createBatch(id, messages);
	}
}
