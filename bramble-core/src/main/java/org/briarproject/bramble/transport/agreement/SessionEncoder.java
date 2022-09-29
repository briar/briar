package org.briarproject.bramble.transport.agreement;

import org.briarproject.bramble.api.data.BdfDictionary;
import org.briarproject.bramble.api.plugin.TransportId;
import org.briarproject.nullsafety.NotNullByDefault;

@NotNullByDefault
interface SessionEncoder {

	BdfDictionary encodeSession(Session s, TransportId transportId);

	BdfDictionary getSessionQuery(TransportId transportId);
}
