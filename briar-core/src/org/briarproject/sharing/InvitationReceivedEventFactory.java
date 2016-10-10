package org.briarproject.sharing;

import org.briarproject.api.event.InvitationRequestReceivedEvent;

public interface InvitationReceivedEventFactory<IS extends InviteeSessionState, IR extends InvitationRequestReceivedEvent> {

	IR build(IS localState, long time, String msg);
}
