package org.briarproject.android.groups;

import static android.text.TextUtils.TruncateAt.END;
import static android.widget.LinearLayout.HORIZONTAL;
import static org.briarproject.android.util.CommonLayoutParams.WRAP_WRAP_1;

import java.util.ArrayList;

import org.briarproject.R;
import org.briarproject.android.util.LayoutUtils;

import android.content.Context;
import android.content.res.Resources;
import android.text.format.DateUtils;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.LinearLayout;
import android.widget.TextView;

class GroupListAdapter extends ArrayAdapter<GroupListItem> {

	private final int pad;

	GroupListAdapter(Context ctx) {
		super(ctx, android.R.layout.simple_expandable_list_item_1,
				new ArrayList<GroupListItem>());
		pad = LayoutUtils.getPadding(ctx);
	}

	@Override
	public View getView(int position, View convertView, ViewGroup parent) {
		GroupListItem item = getItem(position);
		Context ctx = getContext();
		Resources res = ctx.getResources();

		LinearLayout layout = new LinearLayout(ctx);
		layout.setOrientation(HORIZONTAL);
		int unread = item.getUnreadCount();
		if(unread > 0)
			layout.setBackgroundColor(res.getColor(R.color.unread_background));

		TextView name = new TextView(ctx);
		name.setLayoutParams(WRAP_WRAP_1);
		name.setTextSize(18);
		name.setSingleLine();
		name.setEllipsize(END);
		name.setPadding(pad, pad, pad, pad);
		String groupName = item.getGroup().getName();
		if(unread > 0) name.setText(groupName + " (" + unread + ")");
		else name.setText(groupName);
		layout.addView(name);

		if(item.isEmpty()) {
			TextView noPosts = new TextView(ctx);
			noPosts.setPadding(pad, 0, pad, pad);
			noPosts.setTextColor(res.getColor(R.color.no_posts));
			noPosts.setText(R.string.no_posts);
			layout.addView(noPosts);
		} else {
			TextView date = new TextView(ctx);
			date.setPadding(pad, 0, pad, pad);
			long timestamp = item.getTimestamp();
			date.setText(DateUtils.getRelativeTimeSpanString(ctx, timestamp));
			layout.addView(date);
		}

		return layout;
	}
}
