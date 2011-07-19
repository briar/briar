package net.sf.briar.protocol;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.PublicKey;
import java.security.Signature;
import java.security.SignatureException;
import java.util.Collection;
import java.util.Map;

import net.sf.briar.api.protocol.BatchId;
import net.sf.briar.api.protocol.GroupId;
import net.sf.briar.api.protocol.Header;
import net.sf.briar.api.protocol.Tags;
import net.sf.briar.api.serial.FormatException;
import net.sf.briar.api.serial.ObjectReader;
import net.sf.briar.api.serial.Reader;

class HeaderReader implements ObjectReader<Header> {

	private final PublicKey publicKey;
	private final Signature signature;
	private final HeaderFactory headerFactory;

	HeaderReader(PublicKey publicKey, Signature signature,
			HeaderFactory headerFactory) {
		this.publicKey = publicKey;
		this.signature = signature;
		this.headerFactory = headerFactory;
	}

	public Header readObject(Reader reader) throws IOException,
	GeneralSecurityException {
		// Initialise the input stream
		CountingConsumer counting = new CountingConsumer(Header.MAX_SIZE);
		SigningConsumer signing = new SigningConsumer(signature);
		signature.initVerify(publicKey);
		// Read the signed data
		reader.addConsumer(counting);
		reader.addConsumer(signing);
		// Acks
		reader.addObjectReader(Tags.BATCH_ID, new BatchIdReader());
		Collection<BatchId> acks = reader.readList(BatchId.class);
		reader.removeObjectReader(Tags.BATCH_ID);
		// Subs
		reader.addObjectReader(Tags.GROUP_ID, new GroupIdReader());
		Collection<GroupId> subs = reader.readList(GroupId.class);
		reader.removeObjectReader(Tags.GROUP_ID);
		// Transports
		reader.readUserDefinedTag(Tags.TRANSPORTS);
		Map<String, String> transports =
			reader.readMap(String.class, String.class);
		// Timestamp
		reader.readUserDefinedTag(Tags.TIMESTAMP);
		long timestamp = reader.readInt64();
		if(timestamp < 0L) throw new FormatException();
		reader.removeConsumer(signing);
		// Read and verify the signature
		reader.readUserDefinedTag(Tags.SIGNATURE);
		byte[] sig = reader.readRaw();
		reader.removeConsumer(counting);
		if(!signature.verify(sig)) throw new SignatureException();
		// Build and return the header
		return headerFactory.createHeader(acks, subs, transports, timestamp);
	}
}
