package net.sf.briar.protocol;

import java.util.BitSet;
import java.util.Collection;
import java.util.Map;

import net.sf.briar.api.crypto.CryptoComponent;
import net.sf.briar.api.crypto.MessageDigest;
import net.sf.briar.api.protocol.Ack;
import net.sf.briar.api.protocol.BatchId;
import net.sf.briar.api.protocol.Group;
import net.sf.briar.api.protocol.GroupId;
import net.sf.briar.api.protocol.MessageId;
import net.sf.briar.api.protocol.Offer;
import net.sf.briar.api.protocol.PacketFactory;
import net.sf.briar.api.protocol.RawBatch;
import net.sf.briar.api.protocol.Request;
import net.sf.briar.api.protocol.SubscriptionUpdate;
import net.sf.briar.api.protocol.Transport;
import net.sf.briar.api.protocol.TransportUpdate;

import com.google.inject.Inject;

class PacketFactoryImpl implements PacketFactory {

	private final CryptoComponent crypto;

	@Inject
	PacketFactoryImpl(CryptoComponent crypto) {
		this.crypto = crypto;
	}

	public Ack createAck(Collection<BatchId> acked) {
		return new AckImpl(acked);
	}

	public RawBatch createBatch(Collection<byte[]> messages) {
		MessageDigest messageDigest = crypto.getMessageDigest();
		for(byte[] raw : messages) messageDigest.update(raw);
		return new RawBatchImpl(new BatchId(messageDigest.digest()), messages);
	}

	public Offer createOffer(Collection<MessageId> offered) {
		return new OfferImpl(offered);
	}

	public Request createRequest(BitSet requested, int length) {
		return new RequestImpl(requested, length);
	}

	public SubscriptionUpdate createSubscriptionUpdate(
			Map<GroupId, GroupId> holes, Map<Group, Long> subs, long expiry,
			long timestamp) {
		return new SubscriptionUpdateImpl(holes, subs, expiry, timestamp);
	}

	public TransportUpdate createTransportUpdate(
			Collection<Transport> transports, long timestamp) {
		return new TransportUpdateImpl(transports, timestamp);
	}
}
