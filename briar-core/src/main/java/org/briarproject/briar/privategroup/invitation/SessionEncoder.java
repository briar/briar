package org.briarproject.briar.privategroup.invitation;

import org.briarproject.bramble.api.data.BdfDictionary;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;

@NotNullByDefault
interface SessionEncoder {

	BdfDictionary encodeSession(Session s);
}
