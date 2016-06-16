package org.briarproject.sharing;

import org.briarproject.api.event.InvitationReceivedEvent;

public interface InvitationReceivedEventFactory<IS extends InviteeSessionState, IR extends InvitationReceivedEvent> {

	IR build(IS localState);
}
