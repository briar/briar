package org.briarproject.android.sharing;

import android.content.Context;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import org.briarproject.R;
import org.briarproject.android.util.BriarAdapter;
import org.briarproject.android.view.TextAvatarView;
import org.briarproject.api.contact.Contact;
import org.briarproject.api.sharing.InvitationItem;
import org.briarproject.util.StringUtils;

import java.util.ArrayList;
import java.util.Collection;

import static android.view.View.GONE;
import static android.view.View.VISIBLE;

abstract class InvitationAdapter extends
		BriarAdapter<InvitationItem, InvitationAdapter.InvitationsViewHolder> {

	private final AvailableForumClickListener listener;

	InvitationAdapter(Context ctx, AvailableForumClickListener listener) {
		super(ctx, InvitationItem.class);
		this.listener = listener;
	}

	@Override
	public InvitationsViewHolder onCreateViewHolder(ViewGroup parent,
			int viewType) {

		View v = LayoutInflater.from(ctx)
				.inflate(R.layout.list_item_invitations, parent,  false);
		return new InvitationsViewHolder(v);
	}

	@Override
	public void onBindViewHolder(InvitationsViewHolder ui, int position) {
		final InvitationItem item = getItemAt(position);
		if (item == null) return;

		Collection<String> names = new ArrayList<>();
		for (Contact c : item.getNewSharers())
			names.add(c.getAuthor().getName());
		ui.sharedBy.setText(ctx.getString(R.string.shared_by_format,
				StringUtils.join(names, ", ")));

		if (item.isSubscribed()) {
			ui.subscribed.setVisibility(VISIBLE);
		} else {
			ui.subscribed.setVisibility(GONE);
		}

		ui.accept.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				listener.onItemClick(item, true);
			}
		});
		ui.decline.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				listener.onItemClick(item, false);
			}
		});
	}

	@Override
	public boolean areContentsTheSame(InvitationItem oldItem,
			InvitationItem newItem) {
		return oldItem.isSubscribed() == newItem.isSubscribed() &&
				oldItem.getNewSharers().equals(newItem.getNewSharers());
	}

	@Override
	public boolean areItemsTheSame(InvitationItem oldItem,
			InvitationItem newItem) {
		return oldItem.getShareable().equals(newItem.getShareable());
	}

	static class InvitationsViewHolder extends RecyclerView.ViewHolder {

		final TextAvatarView avatar;
		final TextView name;
		private final TextView sharedBy;
		final TextView subscribed;
		private final Button accept;
		private final Button decline;

		private InvitationsViewHolder(View v) {
			super(v);

			avatar = (TextAvatarView) v.findViewById(R.id.avatarView);
			name = (TextView) v.findViewById(R.id.forumNameView);
			sharedBy = (TextView) v.findViewById(R.id.sharedByView);
			subscribed = (TextView) v.findViewById(R.id.forumSubscribedView);
			accept = (Button) v.findViewById(R.id.acceptButton);
			decline = (Button) v.findViewById(R.id.declineButton);
		}
	}

	interface AvailableForumClickListener {
		void onItemClick(InvitationItem item, boolean accept);
	}
}
