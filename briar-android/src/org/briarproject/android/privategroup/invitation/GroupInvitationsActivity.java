package org.briarproject.android.privategroup.invitation;

import android.content.Context;

import org.briarproject.R;
import org.briarproject.android.ActivityComponent;
import org.briarproject.android.sharing.InvitationAdapter;
import org.briarproject.android.sharing.InvitationsActivity;
import org.briarproject.api.privategroup.invitation.GroupInvitationItem;

import javax.inject.Inject;

import static org.briarproject.android.sharing.InvitationAdapter.InvitationClickListener;

public class GroupInvitationsActivity
		extends InvitationsActivity<GroupInvitationItem> {

	@Inject
	protected GroupInvitationsController controller;

	@Override
	public void injectActivity(ActivityComponent component) {
		component.inject(this);
	}

	@Override
	protected GroupInvitationsController getController() {
		return controller;
	}

	@Override
	protected InvitationAdapter<GroupInvitationItem, ?> getAdapter(Context ctx,
			InvitationClickListener<GroupInvitationItem> listener) {
		return new GroupInvitationAdapter(ctx, listener);
	}

	@Override
	protected int getAcceptRes() {
		return R.string.groups_invitations_joined;
	}

	@Override
	protected int getDeclineRes() {
		return R.string.groups_invitations_declined;
	}

}
