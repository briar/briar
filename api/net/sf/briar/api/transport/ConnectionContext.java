package net.sf.briar.api.transport;

import net.sf.briar.api.ContactId;
import net.sf.briar.api.protocol.TransportIndex;

public interface ConnectionContext {

	ContactId getContactId();

	TransportIndex getTransportIndex();

	long getConnectionNumber();
}
