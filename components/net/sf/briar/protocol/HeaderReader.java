package net.sf.briar.protocol;

import java.io.IOException;
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

	public Header readObject(Reader r) throws IOException {
		// Initialise and add the consumer
		CountingConsumer counting = new CountingConsumer(Header.MAX_SIZE);
		r.addConsumer(counting);
		r.readUserDefinedTag(Tags.HEADER);
		// Acks
		r.addObjectReader(Tags.BATCH_ID, new BatchIdReader());
		Collection<BatchId> acks = r.readList(BatchId.class);
		r.removeObjectReader(Tags.BATCH_ID);
		// Subs
		r.addObjectReader(Tags.GROUP_ID, new GroupIdReader());
		Collection<GroupId> subs = r.readList(GroupId.class);
		r.removeObjectReader(Tags.GROUP_ID);
		// Transports
		Map<String, String> transports =
			r.readMap(String.class, String.class);
		// Timestamp
		long timestamp = r.readInt64();
		if(timestamp < 0L) throw new FormatException();
		// Remove the consumer
		r.removeConsumer(counting);
		// Build and return the header
		return headerFactory.createHeader(acks, subs, transports, timestamp);
	}
}
