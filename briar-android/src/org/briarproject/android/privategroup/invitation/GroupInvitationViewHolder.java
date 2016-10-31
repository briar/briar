package org.briarproject.android.privategroup.invitation;

import android.support.annotation.Nullable;
import android.view.View;

import org.briarproject.R;
import org.briarproject.android.sharing.InvitationAdapter.InvitationClickListener;
import org.briarproject.android.sharing.InvitationViewHolder;
import org.briarproject.api.privategroup.invitation.GroupInvitationItem;

public class GroupInvitationViewHolder extends InvitationViewHolder<GroupInvitationItem> {

	public GroupInvitationViewHolder(View v) {
		super(v);
	}

	@Override
	public void onBind(@Nullable final GroupInvitationItem item,
			final InvitationClickListener<GroupInvitationItem> listener) {
		super.onBind(item, listener);
		if (item == null) return;

		sharedBy.setText(
				sharedBy.getContext().getString(R.string.groups_created_by,
						item.getCreator().getAuthor().getName()));
	}

}