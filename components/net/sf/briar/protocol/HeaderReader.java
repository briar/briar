package net.sf.briar.protocol;

import java.io.IOException;
import java.security.GeneralSecurityException;
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

	private final HeaderFactory headerFactory;

	HeaderReader(HeaderFactory headerFactory) {
		this.headerFactory = headerFactory;
	}

	public Header readObject(Reader reader) throws IOException,
	GeneralSecurityException {
		// Initialise and add the consumer
		CountingConsumer counting = new CountingConsumer(Header.MAX_SIZE);
		reader.addConsumer(counting);
		// Acks
		reader.addObjectReader(Tags.BATCH_ID, new BatchIdReader());
		Collection<BatchId> acks = reader.readList(BatchId.class);
		reader.removeObjectReader(Tags.BATCH_ID);
		// Subs
		reader.addObjectReader(Tags.GROUP_ID, new GroupIdReader());
		Collection<GroupId> subs = reader.readList(GroupId.class);
		reader.removeObjectReader(Tags.GROUP_ID);
		// Transports
		Map<String, String> transports =
			reader.readMap(String.class, String.class);
		// Timestamp
		long timestamp = reader.readInt64();
		if(timestamp < 0L) throw new FormatException();
		// Remove the consumer
		reader.removeConsumer(counting);
		// Build and return the header
		return headerFactory.createHeader(acks, subs, transports, timestamp);
	}
}
