package net.sf.briar.protocol;

import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import net.sf.briar.api.protocol.BatchId;
import net.sf.briar.api.protocol.GroupId;
import net.sf.briar.api.protocol.Header;

class HeaderFactoryImpl implements HeaderFactory {

	public Header createHeader(Collection<BatchId> acks,
			Collection<GroupId> subs, Map<String, String> transports,
			long timestamp) {
		Set<BatchId> ackSet = new HashSet<BatchId>(acks);
		Set<GroupId> subSet = new HashSet<GroupId>(subs);
		return new HeaderImpl(ackSet, subSet, transports, timestamp);
	}
}
