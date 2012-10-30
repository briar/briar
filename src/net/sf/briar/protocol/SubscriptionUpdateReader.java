package net.sf.briar.protocol;

import static net.sf.briar.api.protocol.ProtocolConstants.MAX_PACKET_LENGTH;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import net.sf.briar.api.FormatException;
import net.sf.briar.api.protocol.Group;
import net.sf.briar.api.protocol.GroupId;
import net.sf.briar.api.protocol.PacketFactory;
import net.sf.briar.api.protocol.SubscriptionUpdate;
import net.sf.briar.api.protocol.Types;
import net.sf.briar.api.protocol.UniqueId;
import net.sf.briar.api.serial.Consumer;
import net.sf.briar.api.serial.CountingConsumer;
import net.sf.briar.api.serial.Reader;
import net.sf.briar.api.serial.StructReader;

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
		// Holes
		Map<GroupId, GroupId> holes = new HashMap<GroupId, GroupId>();
		r.setMaxBytesLength(UniqueId.LENGTH);
		r.readMapStart();
		while(!r.hasMapEnd()) {
			byte[] start = r.readBytes();
			if(start.length != UniqueId.LENGTH) throw new FormatException();
			byte[] end = r.readBytes();
			if(end.length != UniqueId.LENGTH)throw new FormatException();
			holes.put(new GroupId(start), new GroupId(end));
		}
		r.readMapEnd();
		r.resetMaxBytesLength();
		// Subscriptions
		r.addStructReader(Types.GROUP, groupReader);
		Map<Group, Long> subs = r.readMap(Group.class, Long.class);
		r.removeStructReader(Types.GROUP);
		// Expiry time
		long expiry = r.readInt64();
		if(expiry < 0L) throw new FormatException();
		// Timestamp
		long timestamp = r.readInt64();
		if(timestamp < 0L) throw new FormatException();
		r.removeConsumer(counting);
		// Build and return the subscription update
		return packetFactory.createSubscriptionUpdate(holes, subs, expiry,
				timestamp);
	}
}
