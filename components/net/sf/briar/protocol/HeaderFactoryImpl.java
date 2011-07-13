package net.sf.briar.protocol;

import java.util.Map;
import java.util.Set;

import net.sf.briar.api.protocol.BatchId;
import net.sf.briar.api.protocol.BundleId;
import net.sf.briar.api.protocol.GroupId;
import net.sf.briar.api.protocol.Header;

class HeaderFactoryImpl implements HeaderFactory {

	public Header createHeader(BundleId id, Set<BatchId> acks,
			Set<GroupId> subs, Map<String, String> transports) {
		return new HeaderImpl(id, acks, subs, transports);
	}
}
