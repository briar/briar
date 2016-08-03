package org.briarproject.android.sharing;

import android.content.Context;
import android.support.v7.util.SortedList;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import org.briarproject.R;
import org.briarproject.android.util.TextAvatarView;
import org.briarproject.api.contact.Contact;
import org.briarproject.api.sharing.InvitationItem;
import org.briarproject.util.StringUtils;

import java.util.ArrayList;
import java.util.Collection;

import static android.view.View.GONE;
import static android.view.View.VISIBLE;

abstract class InvitationAdapter extends
		RecyclerView.Adapter<InvitationAdapter.InvitationsViewHolder> {

	protected final Context ctx;
	private final AvailableForumClickListener listener;
	private final SortedList<InvitationItem> invitations =
			new SortedList<>(InvitationItem.class,
					new SortedListCallBacks());

	InvitationAdapter(Context ctx, AvailableForumClickListener listener) {
		this.ctx = ctx;
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
		final InvitationItem item = getItem(position);

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
	public int getItemCount() {
		return invitations.size();
	}

	public InvitationItem getItem(int position) {
		return invitations.get(position);
	}

	public void add(InvitationItem item) {
		invitations.add(item);
	}

	public void addAll(Collection<InvitationItem> list) {
		invitations.addAll(list);
	}

	public void remove(InvitationItem item) {
		invitations.remove(item);
	}

	public void clear() {
		invitations.clear();
	}

	static class InvitationsViewHolder extends RecyclerView.ViewHolder {

		final TextAvatarView avatar;
		final TextView name;
		final TextView sharedBy;
		final TextView subscribed;
		final Button accept;
		final Button decline;

		InvitationsViewHolder(View v) {
			super(v);

			avatar = (TextAvatarView) v.findViewById(R.id.avatarView);
			name = (TextView) v.findViewById(R.id.forumNameView);
			sharedBy = (TextView) v.findViewById(R.id.sharedByView);
			subscribed = (TextView) v.findViewById(R.id.forumSubscribedView);
			accept = (Button) v.findViewById(R.id.acceptButton);
			decline = (Button) v.findViewById(R.id.declineButton);
		}
	}

	abstract int compareInvitations(InvitationItem o1, InvitationItem o2);

	private class SortedListCallBacks
			extends SortedList.Callback<InvitationItem> {

		@Override
		public int compare(InvitationItem o1,
				InvitationItem o2) {
			return compareInvitations(o1, o2);
		}

		@Override
		public void onInserted(int position, int count) {
			notifyItemRangeInserted(position, count);
		}

		@Override
		public void onRemoved(int position, int count) {
			notifyItemRangeRemoved(position, count);
		}

		@Override
		public void onMoved(int fromPosition, int toPosition) {
			notifyItemMoved(fromPosition, toPosition);
		}

		@Override
		public void onChanged(int position, int count) {
			notifyItemRangeChanged(position, count);
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
	}


	interface AvailableForumClickListener {
		void onItemClick(InvitationItem item, boolean accept);
	}
}
