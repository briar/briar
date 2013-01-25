package net.sf.briar.protocol;

import static net.sf.briar.api.protocol.ProtocolConstants.MAX_PACKET_LENGTH;
import static net.sf.briar.api.protocol.Types.GROUP;
import static net.sf.briar.api.protocol.Types.SUBSCRIPTION_UPDATE;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

import net.sf.briar.api.FormatException;
import net.sf.briar.api.protocol.Group;
import net.sf.briar.api.protocol.SubscriptionUpdate;
import net.sf.briar.api.serial.Consumer;
import net.sf.briar.api.serial.CountingConsumer;
import net.sf.briar.api.serial.Reader;
import net.sf.briar.api.serial.StructReader;

class SubscriptionUpdateReader implements StructReader<SubscriptionUpdate> {

	private final StructReader<Group> groupReader;

	SubscriptionUpdateReader(StructReader<Group> groupReader) {
		this.groupReader = groupReader;
	}

	public SubscriptionUpdate readStruct(Reader r) throws IOException {
		Consumer counting = new CountingConsumer(MAX_PACKET_LENGTH);
		r.addConsumer(counting);
		r.readStructId(SUBSCRIPTION_UPDATE);
		// Read the subscriptions
		r.addStructReader(GROUP, groupReader);
		List<Group> subs = r.readList(Group.class);
		r.removeStructReader(GROUP);
		// Read the version number
		long version = r.readInt64();
		if(version < 0L) throw new FormatException();
		r.removeConsumer(counting);
		// Build and return the subscription update
		subs = Collections.unmodifiableList(subs);
		return new SubscriptionUpdate(subs, version);
	}
}
