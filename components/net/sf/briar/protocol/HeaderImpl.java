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
	private final Set<BatchId> acks;
	private final Set<GroupId> subs;
	private final Map<String, String> transports;

	HeaderImpl(BundleId id, Set<BatchId> acks, Set<GroupId> subs,
			Map<String, String> transports) {
		this.id = id;
		this.acks = acks;
		this.subs = subs;
		this.transports = transports;
	}

	public BundleId getId() {
		return id;
	}

	public Set<BatchId> getAcks() {
		return acks;
	}

	public Set<GroupId> getSubscriptions() {
		return subs;
	}

	public Map<String, String> getTransports() {
		return transports;
	}
}
