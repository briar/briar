package org.briarproject.android.privategroup.conversation;

import android.support.annotation.UiThread;
import android.support.v7.widget.LinearLayoutManager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import org.briarproject.R;
import org.briarproject.android.threaded.ThreadItemAdapter;

@UiThread
public class GroupMessageAdapter extends ThreadItemAdapter<GroupMessageItem> {

	public GroupMessageAdapter(ThreadItemListener<GroupMessageItem> listener,
			LinearLayoutManager layoutManager) {
		super(listener, layoutManager);
	}

	@Override
	public GroupMessageViewHolder onCreateViewHolder(ViewGroup parent,
			int viewType) {
		View v = LayoutInflater.from(parent.getContext())
				.inflate(R.layout.list_item_forum_post, parent, false);
		return new GroupMessageViewHolder(v);
	}

}
