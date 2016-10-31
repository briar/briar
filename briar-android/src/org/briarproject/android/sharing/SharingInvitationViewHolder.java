package org.briarproject.android.sharing;

import android.support.annotation.Nullable;
import android.view.View;

import org.briarproject.R;
import org.briarproject.api.contact.Contact;
import org.briarproject.api.sharing.SharingInvitationItem;
import org.briarproject.util.StringUtils;

import java.util.ArrayList;
import java.util.Collection;

public class SharingInvitationViewHolder
		extends InvitationViewHolder<SharingInvitationItem> {

	public SharingInvitationViewHolder(View v) {
		super(v);
	}

	@Override
	public void onBind(@Nullable final SharingInvitationItem item,
			final InvitationAdapter.InvitationClickListener<SharingInvitationItem> listener) {
		super.onBind(item, listener);
		if (item == null) return;

		Collection<String> names = new ArrayList<>();
		for (Contact c : item.getNewSharers())
			names.add(c.getAuthor().getName());
		sharedBy.setText(
				sharedBy.getContext().getString(R.string.shared_by_format,
						StringUtils.join(names, ", ")));
	}

}
