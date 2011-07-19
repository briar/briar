package net.sf.briar.protocol;

import java.util.Collection;
import java.util.Map;

import net.sf.briar.api.protocol.BatchId;
import net.sf.briar.api.protocol.GroupId;
import net.sf.briar.api.protocol.Header;

interface HeaderFactory {

	Header createHeader(Collection<BatchId> acks, Collection<GroupId> subs,
			Map<String, String> transports, long timestamp);
}
