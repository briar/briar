package org.briarproject.briar.sharing;

import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.briar.api.sharing.SharingMessage;

@NotNullByDefault
interface InvitationFactory<I extends SharingMessage.Invitation, SS extends SharerSessionState>
		extends org.briarproject.briar.api.sharing.InvitationFactory<I> {

	I build(SS localState, long time);
}
