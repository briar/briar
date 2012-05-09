package net.sf.briar.protocol;

import static net.sf.briar.api.protocol.ProtocolConstants.MAX_PACKET_LENGTH;

import java.io.IOException;
import java.util.Map;

import net.sf.briar.api.FormatException;
import net.sf.briar.api.protocol.Group;
import net.sf.briar.api.protocol.PacketFactory;
import net.sf.briar.api.protocol.SubscriptionUpdate;
import net.sf.briar.api.protocol.Types;
import net.sf.briar.api.serial.Consumer;
import net.sf.briar.api.serial.CountingConsumer;
import net.sf.briar.api.serial.StructReader;
import net.sf.briar.api.serial.Reader;

class SubscriptionUpdateReader implements StructReader<SubscriptionUpdate> {

	private final StructReader<Group> groupReader;
	private final PacketFactory packetFactory;

	SubscriptionUpdateReader(StructReader<Group> groupReader,
			PacketFactory packetFactory) {
		this.groupReader = groupReader;
		this.packetFactory = packetFactory;
	}

	public SubscriptionUpdate readStruct(Reader r) throws IOException {
		// Initialise the consumer
		Consumer counting = new CountingConsumer(MAX_PACKET_LENGTH);
		// Read the data
		r.addConsumer(counting);
		r.readStructId(Types.SUBSCRIPTION_UPDATE);
		r.addStructReader(Types.GROUP, groupReader);
		Map<Group, Long> subs = r.readMap(Group.class, Long.class);
		r.removeStructReader(Types.GROUP);
		long timestamp = r.readInt64();
		if(timestamp < 0L) throw new FormatException();
		r.removeConsumer(counting);
		// Build and return the subscription update
		return packetFactory.createSubscriptionUpdate(subs, timestamp);
	}
}
