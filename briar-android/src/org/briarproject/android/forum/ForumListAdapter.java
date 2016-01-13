package org.briarproject.android.forum;

import android.content.Context;
import android.content.res.Resources;
import android.text.format.DateUtils;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.briarproject.R;
import org.briarproject.android.util.LayoutUtils;

import java.util.ArrayList;

import static android.text.TextUtils.TruncateAt.END;
import static android.widget.LinearLayout.HORIZONTAL;
import static org.briarproject.android.util.CommonLayoutParams.WRAP_WRAP_1;

public class ForumListAdapter extends ArrayAdapter<ForumListItem> {

	private final int pad;

	public ForumListAdapter(Context ctx) {
		super(ctx, android.R.layout.simple_expandable_list_item_1,
				new ArrayList<ForumListItem>());
		pad = LayoutUtils.getPadding(ctx);
	}

	@Override
	public View getView(int position, View convertView, ViewGroup parent) {
		ForumListItem item = getItem(position);
		Context ctx = getContext();
		Resources res = ctx.getResources();

		LinearLayout layout = new LinearLayout(ctx);
		layout.setOrientation(HORIZONTAL);
		int unread = item.getUnreadCount();
		if (unread > 0)
			layout.setBackgroundColor(res.getColor(R.color.unread_background));

		TextView name = new TextView(ctx);
		name.setLayoutParams(WRAP_WRAP_1);
		name.setTextSize(18);
		name.setSingleLine();
		name.setEllipsize(END);
		name.setPadding(pad, pad, pad, pad);
		String forumName = item.getForum().getName();
		if (unread > 0) name.setText(forumName + " (" + unread + ")");
		else name.setText(forumName);
		layout.addView(name);

		if (item.isEmpty()) {
			TextView noPosts = new TextView(ctx);
			noPosts.setPadding(pad, 0, pad, pad);
			noPosts.setTextColor(res.getColor(R.color.no_posts));
			noPosts.setText(R.string.no_forum_posts);
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
