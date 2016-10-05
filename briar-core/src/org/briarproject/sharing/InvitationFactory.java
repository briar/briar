package org.briarproject.sharing;

import org.briarproject.api.sharing.SharingMessage;

public interface InvitationFactory<I extends SharingMessage.Invitation, SS extends SharerSessionState> extends
		org.briarproject.api.sharing.InvitationFactory<I> {

	I build(SS localState, long time);
}
