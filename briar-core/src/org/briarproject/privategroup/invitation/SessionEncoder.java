package org.briarproject.privategroup.invitation;

import org.briarproject.api.data.BdfDictionary;
import org.briarproject.api.nullsafety.NotNullByDefault;

@NotNullByDefault
interface SessionEncoder {

	BdfDictionary encodeSession(Session s);
}
