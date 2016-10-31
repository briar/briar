package org.briarproject.android.privategroup.conversation;

import android.support.annotation.LayoutRes;
import android.support.annotation.UiThread;
import android.support.v7.widget.LinearLayoutManager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import org.briarproject.R;
import org.briarproject.android.threaded.BaseThreadItemViewHolder;
import org.briarproject.android.threaded.ThreadItemAdapter;
import org.briarproject.android.threaded.ThreadPostViewHolder;

@UiThread
public class GroupMessageAdapter extends ThreadItemAdapter<GroupMessageItem> {

	public GroupMessageAdapter(ThreadItemListener<GroupMessageItem> listener,
			LinearLayoutManager layoutManager) {
		super(listener, layoutManager);
	}

	@LayoutRes
	@Override
	public int getItemViewType(int position) {
		GroupMessageItem item = getVisibleItem(position);
		if (item != null) return item.getLayout();
		return R.layout.list_item_thread;
	}

	@Override
	public BaseThreadItemViewHolder<GroupMessageItem> onCreateViewHolder(
			ViewGroup parent, int type) {
		View v = LayoutInflater.from(parent.getContext())
				.inflate(type, parent, false);
		if (type == R.layout.list_item_thread_notice) {
			return new JoinMessageItemViewHolder(v);
		}
		return new ThreadPostViewHolder<>(v);
	}

}
