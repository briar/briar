package net.sf.briar.api.transport;

import net.sf.briar.api.ContactId;
import net.sf.briar.api.protocol.TransportIndex;

public interface ConnectionContextFactory {

	ConnectionContext createConnectionContext(ContactId c, TransportIndex i,
			long connection);
}
