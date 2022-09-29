package org.briarproject.briar.introduction;

import org.briarproject.bramble.api.data.BdfDictionary;
import org.briarproject.bramble.api.identity.Author;
import org.briarproject.nullsafety.NotNullByDefault;

@NotNullByDefault
interface SessionEncoder {

	BdfDictionary getIntroduceeSessionsByIntroducerQuery(Author introducer);

	BdfDictionary getIntroducerSessionsQuery();

	BdfDictionary encodeIntroducerSession(IntroducerSession s);

	BdfDictionary encodeIntroduceeSession(IntroduceeSession s);

}
