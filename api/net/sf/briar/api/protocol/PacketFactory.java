package net.sf.briar.api.protocol;

import java.util.BitSet;
import java.util.Collection;
import java.util.Map;

public interface PacketFactory {

	Ack createAck(Collection<BatchId> acked);

	RawBatch createBatch(Collection<byte[]> messages);

	Offer createOffer(Collection<MessageId> offered);

	Request createRequest(BitSet requested, int length);

	SubscriptionUpdate createSubscriptionUpdate(Map<GroupId, GroupId> holes,
			Map<Group, Long> subs, long expiry, long timestamp);

	TransportUpdate createTransportUpdate(Collection<Transport> transports,
			long timestamp);
}
