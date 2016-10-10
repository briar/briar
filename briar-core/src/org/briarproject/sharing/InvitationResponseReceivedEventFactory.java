package org.briarproject.sharing;

import org.briarproject.api.event.InvitationResponseReceivedEvent;

public interface InvitationResponseReceivedEventFactory<SS extends SharerSessionState, IRR extends InvitationResponseReceivedEvent> {

	IRR build(SS localState, boolean accept, long time);
}
