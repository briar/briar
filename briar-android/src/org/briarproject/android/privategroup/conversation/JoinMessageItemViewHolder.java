package org.briarproject.android.privategroup.conversation;

import android.support.annotation.UiThread;
import android.view.View;

import org.briarproject.R;
import org.briarproject.android.threaded.BaseThreadItemViewHolder;
import org.briarproject.android.threaded.ThreadItemAdapter;
import org.briarproject.android.threaded.ThreadItemAdapter.ThreadItemListener;
import org.briarproject.api.nullsafety.NotNullByDefault;

@UiThread
@NotNullByDefault
public class JoinMessageItemViewHolder
		extends BaseThreadItemViewHolder<GroupMessageItem> {

	public JoinMessageItemViewHolder(View v) {
		super(v);
	}

	@Override
	public void bind(final ThreadItemAdapter<GroupMessageItem> adapter,
			final ThreadItemListener<GroupMessageItem> listener,
			final GroupMessageItem item, int pos) {
		super.bind(adapter, listener, item, pos);

		textView.setText(getContext().getString(R.string.groups_member_joined));
	}

}
