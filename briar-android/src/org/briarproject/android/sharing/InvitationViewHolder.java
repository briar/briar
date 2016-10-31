package org.briarproject.android.sharing;

import android.support.annotation.CallSuper;
import android.support.annotation.Nullable;
import android.support.v7.widget.RecyclerView;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import org.briarproject.R;
import org.briarproject.android.sharing.InvitationAdapter.InvitationClickListener;
import org.briarproject.android.view.TextAvatarView;
import org.briarproject.api.sharing.InvitationItem;

import static android.view.View.GONE;
import static android.view.View.VISIBLE;

public class InvitationViewHolder<I extends InvitationItem>
		extends RecyclerView.ViewHolder {

	private final TextAvatarView avatar;
	private final TextView name;
	protected final TextView sharedBy;
	private final TextView subscribed;
	private final Button accept;
	private final Button decline;

	public InvitationViewHolder(View v) {
		super(v);

		avatar = (TextAvatarView) v.findViewById(R.id.avatarView);
		name = (TextView) v.findViewById(R.id.forumNameView);
		sharedBy = (TextView) v.findViewById(R.id.sharedByView);
		subscribed = (TextView) v.findViewById(R.id.forumSubscribedView);
		accept = (Button) v.findViewById(R.id.acceptButton);
		decline = (Button) v.findViewById(R.id.declineButton);
	}

	@CallSuper
	public void onBind(@Nullable final I item,
			final InvitationClickListener<I> listener) {
		if (item == null) return;

		avatar.setText(item.getShareable().getName().substring(0, 1));
		avatar.setBackgroundBytes(item.getShareable().getId().getBytes());

		name.setText(item.getShareable().getName());

		if (item.isSubscribed()) {
			subscribed.setVisibility(VISIBLE);
		} else {
			subscribed.setVisibility(GONE);
		}

		accept.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				listener.onItemClick(item, true);
			}
		});
		decline.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				listener.onItemClick(item, false);
			}
		});
	}

}