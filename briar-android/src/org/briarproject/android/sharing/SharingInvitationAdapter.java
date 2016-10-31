package org.briarproject.android.sharing;

import android.content.Context;
import android.view.ViewGroup;

import org.briarproject.api.sharing.SharingInvitationItem;

class SharingInvitationAdapter extends
		InvitationAdapter<SharingInvitationItem, SharingInvitationViewHolder> {

	SharingInvitationAdapter(Context ctx, InvitationClickListener listener) {
		super(ctx, SharingInvitationItem.class, listener);
	}

	@Override
	public SharingInvitationViewHolder onCreateViewHolder(
			ViewGroup parent,
			int viewType) {
		return new SharingInvitationViewHolder(getView(parent));
	}

	@Override
	public boolean areContentsTheSame(SharingInvitationItem oldItem,
			SharingInvitationItem newItem) {
		return oldItem.isSubscribed() == newItem.isSubscribed() &&
				oldItem.getNewSharers().equals(newItem.getNewSharers());
	}

}
