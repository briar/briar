package org.briarproject.briar.sharing;

import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.briar.api.sharing.event.InvitationResponseReceivedEvent;

@Deprecated
@NotNullByDefault
interface InvitationResponseReceivedEventFactory<SS extends SharerSessionState, IRR extends InvitationResponseReceivedEvent> {

	IRR build(SS localState, boolean accept, long time);
}
