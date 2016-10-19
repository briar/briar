package org.briarproject.android.forum;

import android.support.annotation.UiThread;
import android.support.v7.widget.LinearLayoutManager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import org.briarproject.R;
import org.briarproject.android.threaded.ThreadItemAdapter;

@UiThread
public class NestedForumAdapter extends ThreadItemAdapter<ForumItem> {

	public NestedForumAdapter(ThreadItemListener<ForumItem> listener,
			LinearLayoutManager layoutManager) {
		super(listener, layoutManager);
	}

	@Override
	public NestedForumHolder onCreateViewHolder(ViewGroup parent,
			int viewType) {
		View v = LayoutInflater.from(parent.getContext())
				.inflate(R.layout.list_item_forum_post, parent, false);
		return new NestedForumHolder(v);
	}

}
