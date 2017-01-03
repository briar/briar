package org.briarproject.briar.sharing;

import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.briar.api.sharing.event.InvitationRequestReceivedEvent;

import javax.annotation.Nullable;

@Deprecated
@NotNullByDefault
interface InvitationReceivedEventFactory<IS extends InviteeSessionState, IR extends InvitationRequestReceivedEvent> {

	IR build(IS localState, long time, @Nullable String msg);
}
