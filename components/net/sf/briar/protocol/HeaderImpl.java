package net.sf.briar.protocol;

import java.util.Map;
import java.util.Set;

import net.sf.briar.api.protocol.BatchId;
import net.sf.briar.api.protocol.GroupId;
import net.sf.briar.api.protocol.Header;

/** A simple in-memory implementation of a header. */
class HeaderImpl implements Header {

	private final Set<BatchId> acks;
	private final Set<GroupId> subs;
	private final Map<String, String> transports;
	private final long timestamp;

	HeaderImpl(Set<BatchId> acks, Set<GroupId> subs,
			Map<String, String> transports, long timestamp) {
		this.acks = acks;
		this.subs = subs;
		this.transports = transports;
		this.timestamp = timestamp;
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

	public long getTimestamp() {
		return timestamp;
	}
}
