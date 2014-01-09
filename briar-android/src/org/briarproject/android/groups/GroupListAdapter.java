package org.briarproject.android.groups;

import static android.view.Gravity.CENTER;
import static android.widget.LinearLayout.HORIZONTAL;
import static java.text.DateFormat.SHORT;
import static org.briarproject.android.groups.GroupListItem.MANAGE;
import static org.briarproject.android.util.CommonLayoutParams.WRAP_WRAP_1;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import org.briarproject.R;
import org.briarproject.android.util.LayoutUtils;

import android.content.Context;
import android.content.res.Resources;
import android.text.format.DateUtils;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.LinearLayout;
import android.widget.TextView;

class GroupListAdapter extends BaseAdapter {

	private final Context ctx;
	private final int pad;
	private final List<GroupListItem> list = new ArrayList<GroupListItem>();
	private int available = 0;

	GroupListAdapter(Context ctx) {
		this.ctx = ctx;
		pad = LayoutUtils.getPadding(ctx);
	}

	public void setAvailable(int available) {
		this.available = available;
	}

	public void add(GroupListItem item) {
		list.add(item);
	}

	public void clear() {
		list.clear();
	}

	public int getCount() {
		return available == 0 ? list.size() : list.size() + 1;
	}

	public GroupListItem getItem(int position) {
		return position == list.size() ? MANAGE : list.get(position);
	}

	public long getItemId(int position) {
		return android.R.layout.simple_expandable_list_item_1;
	}

	public View getView(int position, View convertView, ViewGroup parent) {
		GroupListItem item = getItem(position);
		Resources res = ctx.getResources();

		if(item == MANAGE) {
			TextView manage = new TextView(ctx);
			manage.setGravity(CENTER);
			manage.setTextSize(18);
			manage.setPadding(pad, pad, pad, pad);
			String format = res.getQuantityString(R.plurals.forums_available,
					available);
			manage.setText(String.format(format, available));
			return manage;
		}

		LinearLayout layout = new LinearLayout(ctx);
		layout.setOrientation(HORIZONTAL);
		if(item.getUnreadCount() > 0)
			layout.setBackgroundColor(res.getColor(R.color.unread_background));

		TextView name = new TextView(ctx);
		// Give me all the unused width
		name.setLayoutParams(WRAP_WRAP_1);
		name.setTextSize(18);
		name.setMaxLines(1);
		name.setPadding(pad, pad, pad, pad);
		int unread = item.getUnreadCount();
		String groupName = item.getGroup().getName();
		if(unread > 0) name.setText(groupName + " (" + unread + ")");
		else name.setText(groupName);
		layout.addView(name);

		if(item.isEmpty()) {
			TextView noPosts = new TextView(ctx);
			noPosts.setTextSize(14);
			noPosts.setPadding(pad, 0, pad, pad);
			noPosts.setTextColor(res.getColor(R.color.no_posts));
			noPosts.setText(R.string.no_posts);
			layout.addView(noPosts);
		} else {
			TextView date = new TextView(ctx);
			date.setTextSize(14);
			date.setPadding(pad, 0, pad, pad);
			long then = item.getTimestamp(), now = System.currentTimeMillis();
			date.setText(DateUtils.formatSameDayTime(then, now, SHORT, SHORT));
			layout.addView(date);
		}

		return layout;
	}

	@Override
	public boolean isEmpty() {
		return list.isEmpty() && available == 0;
	}

	public void remove(GroupListItem item) {
		list.remove(item);
	}

	public void sort(Comparator<GroupListItem> comparator) {
		Collections.sort(list, comparator);
	}
}
