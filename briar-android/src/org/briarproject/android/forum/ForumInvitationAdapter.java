package org.briarproject.android.forum;

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
import org.briarproject.util.StringUtils;

import java.util.ArrayList;
import java.util.Collection;

import static android.view.View.GONE;
import static android.view.View.VISIBLE;

class ForumInvitationAdapter extends
		RecyclerView.Adapter<ForumInvitationAdapter.AvailableForumViewHolder> {

	private final Context ctx;
	private final AvailableForumClickListener listener;
	private final SortedList<ForumInvitationItem> forums =
			new SortedList<>(ForumInvitationItem.class,
					new SortedListCallBacks());

	ForumInvitationAdapter(Context ctx, AvailableForumClickListener listener) {
		this.ctx = ctx;
		this.listener = listener;
	}

	@Override
	public AvailableForumViewHolder onCreateViewHolder(ViewGroup parent,
			int viewType) {

		View v = LayoutInflater.from(ctx)
				.inflate(R.layout.list_item_available_forum, parent,  false);
		return new AvailableForumViewHolder(v);
	}

	@Override
	public void onBindViewHolder(AvailableForumViewHolder ui, int position) {
		final ForumInvitationItem item = getItem(position);

		ui.avatar.setText(item.getForum().getName().substring(0, 1));
		ui.avatar.setBackgroundBytes(item.getForum().getId().getBytes());

		ui.name.setText(item.getForum().getName());

		Collection<String> names = new ArrayList<>();
		for (Contact c : item.getContacts()) names.add(c.getAuthor().getName());
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
		return forums.size();
	}

	public ForumInvitationItem getItem(int position) {
		return forums.get(position);
	}

	public void add(ForumInvitationItem item) {
		forums.add(item);
	}

	public void addAll(Collection<ForumInvitationItem> list) {
		forums.addAll(list);
	}

	public void clear() {
		forums.clear();
	}

	static class AvailableForumViewHolder
			extends RecyclerView.ViewHolder {

		private final TextAvatarView avatar;
		private final TextView name;
		private final TextView sharedBy;
		private final TextView subscribed;
		private final Button accept;
		private final Button decline;

		AvailableForumViewHolder(View v) {
			super(v);

			avatar = (TextAvatarView) v.findViewById(R.id.avatarView);
			name = (TextView) v.findViewById(R.id.forumNameView);
			sharedBy = (TextView) v.findViewById(R.id.sharedByView);
			subscribed = (TextView) v.findViewById(R.id.forumSubscribedView);
			accept = (Button) v.findViewById(R.id.acceptButton);
			decline = (Button) v.findViewById(R.id.declineButton);
		}
	}

	private class SortedListCallBacks
			extends SortedList.Callback<ForumInvitationItem> {

		@Override
		public int compare(ForumInvitationItem o1,
				ForumInvitationItem o2) {
			return String.CASE_INSENSITIVE_ORDER
					.compare(o1.getForum().getName(),
							o2.getForum().getName());
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
		public boolean areContentsTheSame(ForumInvitationItem oldItem,
				ForumInvitationItem newItem) {
			return oldItem.getForum().equals(newItem.getForum()) &&
					oldItem.getContacts().equals(newItem.getContacts());
		}

		@Override
		public boolean areItemsTheSame(ForumInvitationItem oldItem,
				ForumInvitationItem newItem) {
			return oldItem.getForum().equals(newItem.getForum());
		}
	}


	interface AvailableForumClickListener {
		void onItemClick(ForumInvitationItem item, boolean accept);
	}
}
