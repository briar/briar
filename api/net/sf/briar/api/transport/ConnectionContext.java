package net.sf.briar.api.transport;

import net.sf.briar.api.ContactId;
import net.sf.briar.api.protocol.TransportId;
import net.sf.briar.api.protocol.TransportIndex;

public interface ConnectionContext {

	ContactId getContactId();

	TransportId getTransportId();

	TransportIndex getTransportIndex();

	long getConnectionNumber();
}
