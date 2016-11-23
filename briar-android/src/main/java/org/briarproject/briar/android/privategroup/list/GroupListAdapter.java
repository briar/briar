package org.briarproject.briar.android.privategroup.list;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import org.briarproject.bramble.api.nullsafety.MethodsNotNullByDefault;
import org.briarproject.bramble.api.nullsafety.ParametersNotNullByDefault;
import org.briarproject.bramble.api.sync.GroupId;
import org.briarproject.briar.R;
import org.briarproject.briar.android.privategroup.list.GroupViewHolder.OnGroupRemoveClickListener;
import org.briarproject.briar.android.util.BriarAdapter;

import static android.support.v7.util.SortedList.INVALID_POSITION;

@MethodsNotNullByDefault
@ParametersNotNullByDefault
class GroupListAdapter extends BriarAdapter<GroupItem, GroupViewHolder> {

	private final OnGroupRemoveClickListener listener;

	GroupListAdapter(Context ctx, OnGroupRemoveClickListener listener) {
		super(ctx, GroupItem.class);
		this.listener = listener;
	}

	@Override
	public GroupViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
		View v = LayoutInflater.from(ctx).inflate(
				R.layout.list_item_group, parent, false);
		return new GroupViewHolder(v);
	}

	@Override
	public void onBindViewHolder(GroupViewHolder ui, int position) {
		ui.bindView(ctx, items.get(position), listener);
	}

	@Override
	public int compare(GroupItem a, GroupItem b) {
		if (a == b) return 0;
		// The group with the latest message comes first
		long aTime = a.getTimestamp(), bTime = b.getTimestamp();
		if (aTime > bTime) return -1;
		if (aTime < bTime) return 1;
		// Break ties by group name
		String aName = a.getName();
		String bName = b.getName();
		return String.CASE_INSENSITIVE_ORDER.compare(aName, bName);
	}

	@Override
	public boolean areContentsTheSame(GroupItem a, GroupItem b) {
		return a.getMessageCount() == b.getMessageCount() &&
				a.getTimestamp() == b.getTimestamp() &&
				a.getUnreadCount() == b.getUnreadCount() &&
				a.isDissolved() == b.isDissolved();
	}

	@Override
	public boolean areItemsTheSame(GroupItem a, GroupItem b) {
		return a.getId().equals(b.getId());
	}

	int findItemPosition(GroupId g) {
		for (int i = 0; i < items.size(); i++) {
			GroupItem item = items.get(i);
			if (item.getId().equals(g)) {
				return i;
			}
		}
		return INVALID_POSITION;
	}

	void removeItem(GroupId groupId) {
		int pos = findItemPosition(groupId);
		if (pos != INVALID_POSITION) items.removeItemAt(pos);
	}

}
