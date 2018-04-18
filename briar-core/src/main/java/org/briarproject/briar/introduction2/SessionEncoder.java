package org.briarproject.briar.introduction2;

import org.briarproject.bramble.api.data.BdfDictionary;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;

@NotNullByDefault
interface SessionEncoder {

	BdfDictionary encodeIntroducerSession(IntroducerSession s);

	BdfDictionary encodeIntroduceeSession(IntroduceeSession s);

}
