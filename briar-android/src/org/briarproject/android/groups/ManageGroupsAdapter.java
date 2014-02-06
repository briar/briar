package org.briarproject.android.groups;

import static android.text.TextUtils.TruncateAt.END;
import static android.view.Gravity.CENTER;
import static android.view.View.INVISIBLE;
import static android.widget.LinearLayout.HORIZONTAL;
import static android.widget.LinearLayout.VERTICAL;
import static org.briarproject.android.groups.ManageGroupsItem.NONE;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import org.briarproject.R;
import org.briarproject.android.util.LayoutUtils;
import org.briarproject.api.messaging.GroupStatus;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

class ManageGroupsAdapter extends BaseAdapter {

	private final Context ctx;
	private final int pad;
	private final List<ManageGroupsItem> list =
			new ArrayList<ManageGroupsItem>();

	ManageGroupsAdapter(Context ctx) {
		this.ctx = ctx;
		pad = LayoutUtils.getPadding(ctx);
	}

	public void add(ManageGroupsItem item) {
		list.add(item);
	}

	public void clear() {
		list.clear();
	}

	public int getCount() {
		return list.isEmpty() ? 1 : list.size();
	}

	public ManageGroupsItem getItem(int position) {
		return list.isEmpty() ? NONE : list.get(position);
	}

	public long getItemId(int position) {
		return android.R.layout.simple_expandable_list_item_1;
	}

	public View getView(int position, View convertView, ViewGroup parent) {
		ManageGroupsItem item = getItem(position);

		if(item == NONE) {
			TextView none = new TextView(ctx);
			none.setGravity(CENTER);
			none.setTextSize(18);
			none.setPadding(pad, pad, pad, pad);
			none.setText(R.string.no_forums_available);
			return none;
		}

		GroupStatus s = item.getGroupStatus();
		LinearLayout layout = new LinearLayout(ctx);
		layout.setOrientation(HORIZONTAL);

		ImageView subscribed = new ImageView(ctx);
		subscribed.setPadding(pad, pad, pad, pad);
		subscribed.setImageResource(R.drawable.navigation_accept);
		if(!s.isSubscribed()) subscribed.setVisibility(INVISIBLE);
		layout.addView(subscribed);

		LinearLayout innerLayout = new LinearLayout(ctx);
		innerLayout.setOrientation(VERTICAL);

		TextView name = new TextView(ctx);
		name.setTextSize(18);
		name.setSingleLine();
		name.setEllipsize(END);
		name.setPadding(0, pad, pad, pad);
		name.setText(s.getGroup().getName());
		innerLayout.addView(name);

		TextView status = new TextView(ctx);
		status.setTextSize(14);
		status.setPadding(0, 0, pad, pad);
		if(s.isSubscribed()) {
			if(s.isVisibleToAll()) status.setText(R.string.subscribed_all);
			else status.setText(R.string.subscribed_some);
		} else {
			status.setText(R.string.not_subscribed);
		}
		innerLayout.addView(status);
		layout.addView(innerLayout);

		return layout;
	}

	@Override
	public boolean isEmpty() {
		return false;
	}

	public void remove(ManageGroupsItem item) {
		list.remove(item);
	}

	public void sort(Comparator<ManageGroupsItem> comparator) {
		Collections.sort(list, comparator);
	}
}
