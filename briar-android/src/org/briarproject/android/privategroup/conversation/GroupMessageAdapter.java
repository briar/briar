package org.briarproject.android.privategroup.conversation;

import android.support.annotation.UiThread;
import android.support.v7.widget.LinearLayoutManager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import org.briarproject.R;
import org.briarproject.android.threaded.BaseThreadItemViewHolder;
import org.briarproject.android.threaded.ThreadItemAdapter;
import org.briarproject.android.threaded.ThreadItemViewHolder;

@UiThread
public class GroupMessageAdapter extends ThreadItemAdapter<GroupMessageItem> {

	public GroupMessageAdapter(ThreadItemListener<GroupMessageItem> listener,
			LinearLayoutManager layoutManager) {
		super(listener, layoutManager);
	}

	@Override
	public int getItemViewType(int position) {
		GroupMessageItem item = getVisibleItem(position);
		if (item instanceof JoinMessageItem) {
			return R.layout.list_item_thread_notice;
		}
		return R.layout.list_item_thread;
	}

	@Override
	public BaseThreadItemViewHolder<GroupMessageItem> onCreateViewHolder(
			ViewGroup parent, int type) {
		View v = LayoutInflater.from(parent.getContext())
				.inflate(type, parent, false);
		if (type == R.layout.list_item_thread_notice) {
			return new BaseThreadItemViewHolder<>(v);
		}
		return new ThreadItemViewHolder<>(v);
	}

}
