package org.briarproject.briar.android.privategroup.conversation;

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

import androidx.annotation.LayoutRes;
import androidx.annotation.UiThread;

import static androidx.recyclerview.widget.RecyclerView.NO_POSITION;

@UiThread
@NotNullByDefault
class GroupMessageAdapter extends ThreadItemAdapter<GroupMessageItem> {

	private boolean isCreator = false;

	GroupMessageAdapter(ThreadItemListener<GroupMessageItem> listener) {
		super(listener);
	}

	@LayoutRes
	@Override
	public int getItemViewType(int position) {
		GroupMessageItem item = getItem(position);
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

	void setIsCreator(boolean isCreator) {
		this.isCreator = isCreator;
		notifyDataSetChanged();
	}

	void updateVisibility(AuthorId memberId, Visibility v) {
		int position = findItemPosition(memberId);
		if (position != NO_POSITION) {
			GroupMessageItem item = getItem(position);
			if (item instanceof JoinMessageItem) {
				((JoinMessageItem) item).setVisibility(v);
				notifyItemChanged(findItemPosition(item.getId()), item);
			}
		}
	}

	@Deprecated
	private int findItemPosition(AuthorId a) {
		for (int i = 0; i < getItemCount(); i++) {
			GroupMessageItem item = getItem(i);
			if (item.getAuthor().getId().equals(a))
				return i;
		}
		return NO_POSITION; // Not found
	}

}
