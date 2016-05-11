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

class AvailableForumsAdapter extends
		RecyclerView.Adapter<AvailableForumsAdapter.AvailableForumViewHolder> {

	private final Context ctx;
	private final AvailableForumClickListener listener;
	private final SortedList<AvailableForumsItem> forums =
			new SortedList<>(AvailableForumsItem.class,
					new SortedListCallBacks());

	AvailableForumsAdapter(Context ctx, AvailableForumClickListener listener) {
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
		final AvailableForumsItem item = getItem(position);

		ui.avatar.setText(item.getForum().getName().substring(0, 1));
		ui.avatar.setBackgroundBytes(item.getForum().getId().getBytes());

		ui.name.setText(item.getForum().getName());

		Collection<String> names = new ArrayList<>();
		for (Contact c : item.getContacts()) names.add(c.getAuthor().getName());
		ui.sharedBy.setText(ctx.getString(R.string.shared_by_format,
				StringUtils.join(names, ", ")));

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

	public AvailableForumsItem getItem(int position) {
		return forums.get(position);
	}

	public void add(AvailableForumsItem item) {
		forums.add(item);
	}

	public void addAll(Collection<AvailableForumsItem> list) {
		forums.addAll(list);
	}

	public void clear() {
		forums.clear();
	}

	protected static class AvailableForumViewHolder
			extends RecyclerView.ViewHolder {

		private final TextAvatarView avatar;
		private final TextView name;
		private final TextView sharedBy;
		private final Button accept;
		private final Button decline;

		public AvailableForumViewHolder(View v) {
			super(v);

			avatar = (TextAvatarView) v.findViewById(R.id.avatarView);
			name = (TextView) v.findViewById(R.id.forumNameView);
			sharedBy = (TextView) v.findViewById(R.id.sharedByView);
			accept = (Button) v.findViewById(R.id.acceptButton);
			decline = (Button) v.findViewById(R.id.declineButton);
		}
	}

	private class SortedListCallBacks
			extends SortedList.Callback<AvailableForumsItem> {

		@Override
		public int compare(AvailableForumsItem o1,
				AvailableForumsItem o2) {
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
		public boolean areContentsTheSame(AvailableForumsItem oldItem,
				AvailableForumsItem newItem) {
			return oldItem.getForum().equals(newItem.getForum()) &&
					oldItem.getContacts().equals(newItem.getContacts());
		}

		@Override
		public boolean areItemsTheSame(AvailableForumsItem oldItem,
				AvailableForumsItem newItem) {
			return oldItem.getForum().equals(newItem.getForum());
		}
	}


	interface AvailableForumClickListener {
		void onItemClick(AvailableForumsItem item, boolean accept);
	}
}
