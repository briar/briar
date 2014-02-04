package org.briarproject.android.groups;

import static android.widget.LinearLayout.HORIZONTAL;
import static java.text.DateFormat.SHORT;
import static org.briarproject.android.util.CommonLayoutParams.WRAP_WRAP_1;

import java.util.ArrayList;

import org.briarproject.R;
import org.briarproject.android.util.AuthorView;
import org.briarproject.android.util.LayoutUtils;
import org.briarproject.api.Author;
import org.briarproject.api.db.MessageHeader;

import android.content.Context;
import android.content.res.Resources;
import android.text.format.DateUtils;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.LinearLayout;
import android.widget.TextView;

class GroupAdapter extends ArrayAdapter<MessageHeader> {

	private final int pad;

	GroupAdapter(Context ctx) {
		super(ctx, android.R.layout.simple_expandable_list_item_1,
				new ArrayList<MessageHeader>());
		pad = LayoutUtils.getPadding(ctx);
	}

	@Override
	public View getView(int position, View convertView, ViewGroup parent) {
		MessageHeader header = getItem(position);
		Context ctx = getContext();

		LinearLayout layout = new LinearLayout(ctx);
		layout.setOrientation(HORIZONTAL);
		if(!header.isRead()) {
			Resources res = ctx.getResources();
			layout.setBackgroundColor(res.getColor(R.color.unread_background));
		}

		AuthorView authorView = new AuthorView(ctx);
		authorView.setLayoutParams(WRAP_WRAP_1);
		Author author = header.getAuthor();
		if(author == null) authorView.init(null, header.getAuthorStatus());
		else authorView.init(author.getName(), header.getAuthorStatus());
		layout.addView(authorView);

		TextView date = new TextView(ctx);
		date.setTextSize(14);
		date.setPadding(0, pad, pad, pad);
		long then = header.getTimestamp(), now = System.currentTimeMillis();
		date.setText(DateUtils.formatSameDayTime(then, now, SHORT, SHORT));
		layout.addView(date);

		return layout;
	}
}
