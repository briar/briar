package org.briarproject.briar.android.privategroup.conversation;

import android.support.annotation.LayoutRes;
import android.support.annotation.UiThread;
import android.support.v7.widget.LinearLayoutManager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import org.briarproject.bramble.api.identity.AuthorId;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.briar.R;
import org.briarproject.briar.android.threaded.BaseThreadItemViewHolder;
import org.briarproject.briar.android.threaded.ThreadItemAdapter;
import org.briarproject.briar.android.threaded.ThreadPostViewHolder;
import org.briarproject.briar.api.privategroup.Visibility;

import static android.support.v7.widget.RecyclerView.NO_POSITION;

@UiThread
@NotNullByDefault
class GroupMessageAdapter extends ThreadItemAdapter<GroupMessageItem> {

	private boolean isCreator = false;

	GroupMessageAdapter(ThreadItemListener<GroupMessageItem> listener,
			LinearLayoutManager layoutManager) {
		super(listener, layoutManager);
	}

	@LayoutRes
	@Override
	public int getItemViewType(int position) {
		GroupMessageItem item = items.get(position);
		return item.getLayout();
	}

	@Override
	public BaseThreadItemViewHolder<GroupMessageItem> onCreateViewHolder(
			ViewGroup parent, int type) {
		View v = LayoutInflater.from(parent.getContext())
				.inflate(type, parent, false);
		if (type == R.layout.list_item_group_join_notice) {
			return new JoinMessageItemViewHolder(v, isCreator);
		}
		return new ThreadPostViewHolder<>(v);
	}

	void setPerspective(boolean isCreator) {
		this.isCreator = isCreator;
		notifyDataSetChanged();
	}

	void updateVisibility(AuthorId memberId, Visibility v) {
		int position = findItemPosition(memberId);
		if (position != NO_POSITION) {
			GroupMessageItem item = items.get(position);
			if (item instanceof JoinMessageItem) {
				((JoinMessageItem) item).setVisibility(v);
				notifyItemChanged(findItemPosition(item), item);
			}
		}
	}

	private int findItemPosition(AuthorId a) {
		int count = items.size();
		for (int i = 0; i < count; i++) {
			GroupMessageItem item = items.get(i);
			if (item.getAuthor().getId().equals(a))
				return i;
		}
		return NO_POSITION; // Not found
	}

}
