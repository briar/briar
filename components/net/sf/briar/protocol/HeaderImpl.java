package net.sf.briar.protocol;

import java.util.Map;
import java.util.Set;

import net.sf.briar.api.protocol.BatchId;
import net.sf.briar.api.protocol.BundleId;
import net.sf.briar.api.protocol.GroupId;
import net.sf.briar.api.protocol.Header;

/** A simple in-memory implementation of a header. */
class HeaderImpl implements Header {

	private final BundleId id;
	private final long size;
	private final Set<BatchId> acks;
	private final Set<GroupId> subscriptions;
	private final Map<String, String> transports;
	private final byte[] signature;

	HeaderImpl(BundleId id, long size, Set<BatchId> acks,
			Set<GroupId> subscriptions, Map<String, String> transports,
			byte[] signature) {
		this.id = id;
		this.size = size;
		this.acks = acks;
		this.subscriptions = subscriptions;
		this.transports = transports;
		this.signature = signature;
	}

	public BundleId getId() {
		return id;
	}

	public long getSize() {
		return size;
	}

	public Set<BatchId> getAcks() {
		return acks;
	}

	public Set<GroupId> getSubscriptions() {
		return subscriptions;
	}

	public Map<String, String> getTransports() {
		return transports;
	}

	public byte[] getSignature() {
		return signature;
	}
}
