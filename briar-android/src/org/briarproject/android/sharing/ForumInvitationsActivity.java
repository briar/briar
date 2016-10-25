package org.briarproject.android.sharing;

import android.content.Context;

import org.briarproject.R;
import org.briarproject.android.ActivityComponent;
import org.briarproject.api.sharing.SharingInvitationItem;

import javax.inject.Inject;

import static org.briarproject.android.sharing.InvitationAdapter.InvitationClickListener;

public class ForumInvitationsActivity
		extends InvitationsActivity<SharingInvitationItem> {

	@Inject
	ForumInvitationsController controller;

	@Override
	public void injectActivity(ActivityComponent component) {
		component.inject(this);
	}

	@Override
	protected InvitationsController<SharingInvitationItem> getController() {
		return controller;
	}

	@Override
	protected InvitationAdapter<SharingInvitationItem, ?> getAdapter(
			Context ctx,
			InvitationClickListener<SharingInvitationItem> listener) {
		return new SharingInvitationAdapter(ctx, listener);
	}

	@Override
	protected int getAcceptRes() {
		return R.string.forum_joined_toast;
	}

	@Override
	protected int getDeclineRes() {
		return R.string.forum_declined_toast;
	}

}
