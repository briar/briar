package org.briarproject.briar.android.privategroup.invitation;

import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.briar.android.sharing.InvitationController;
import org.briarproject.briar.api.privategroup.invitation.GroupInvitationItem;

@NotNullByDefault
interface GroupInvitationController
		extends InvitationController<GroupInvitationItem> {
}
