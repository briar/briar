package net.sf.briar.android.groups;

import static android.widget.LinearLayout.HORIZONTAL;
import static java.text.DateFormat.SHORT;
import static net.sf.briar.android.util.CommonLayoutParams.WRAP_WRAP_1;

import java.util.ArrayList;

import net.sf.briar.R;
import net.sf.briar.api.Author;
import net.sf.briar.api.db.GroupMessageHeader;
import android.content.Context;
import android.content.res.Resources;
import android.text.format.DateUtils;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.LinearLayout;
import android.widget.TextView;

class GroupAdapter extends ArrayAdapter<GroupMessageHeader> {

	GroupAdapter(Context ctx) {
		super(ctx, android.R.layout.simple_expandable_list_item_1,
				new ArrayList<GroupMessageHeader>());
	}

	@Override
	public View getView(int position, View convertView, ViewGroup parent) {
		GroupMessageHeader item = getItem(position);
		Context ctx = getContext();
		Resources res = ctx.getResources();

		LinearLayout layout = new LinearLayout(ctx);
		layout.setOrientation(HORIZONTAL);
		if(!item.isRead())
			layout.setBackgroundColor(res.getColor(R.color.unread_background));

		TextView name = new TextView(ctx);
		// Give me all the unused width
		name.setLayoutParams(WRAP_WRAP_1);
		name.setTextSize(18);
		name.setMaxLines(1);
		name.setPadding(10, 10, 10, 10);
		Author author = item.getAuthor();
		if(author == null) {
			name.setTextColor(res.getColor(R.color.anonymous_author));
			name.setText(R.string.anonymous);
		} else {
			name.setText(author.getName());
		}
		layout.addView(name);

		TextView date = new TextView(ctx);
		date.setTextSize(14);
		date.setPadding(10, 10, 10, 10);
		long then = item.getTimestamp(), now = System.currentTimeMillis();
		date.setText(DateUtils.formatSameDayTime(then, now, SHORT, SHORT));
		layout.addView(date);

		return layout;
	}
}
