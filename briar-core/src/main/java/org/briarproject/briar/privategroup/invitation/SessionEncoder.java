package org.briarproject.briar.privategroup.invitation;

import org.briarproject.bramble.api.data.BdfDictionary;
import org.briarproject.nullsafety.NotNullByDefault;

@NotNullByDefault
interface SessionEncoder {

	BdfDictionary encodeSession(Session s);
}
