package org.briarproject.android.groups;

import static android.view.Gravity.CENTER_HORIZONTAL;
import static android.view.Gravity.CENTER_VERTICAL;
import static android.widget.LinearLayout.HORIZONTAL;
import static android.widget.LinearLayout.VERTICAL;
import static org.briarproject.android.util.CommonLayoutParams.WRAP_WRAP_1;

import java.util.ArrayList;

import org.briarproject.R;
import org.briarproject.android.util.AuthorView;
import org.briarproject.android.util.LayoutUtils;
import org.briarproject.api.Author;
import org.briarproject.api.db.MessageHeader;
import org.briarproject.util.StringUtils;

import android.content.Context;
import android.content.res.Resources;
import android.text.format.DateUtils;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;

class GroupAdapter extends ArrayAdapter<GroupItem> {

	private final int pad;

	GroupAdapter(Context ctx) {
		super(ctx, android.R.layout.simple_expandable_list_item_1,
				new ArrayList<GroupItem>());
		pad = LayoutUtils.getPadding(ctx);
	}

	@Override
	public View getView(int position, View convertView, ViewGroup parent) {
		GroupItem item = getItem(position);
		MessageHeader header = item.getHeader();
		Context ctx = getContext();
		Resources res = ctx.getResources();

		LinearLayout layout = new LinearLayout(ctx);
		layout.setOrientation(VERTICAL);
		layout.setGravity(CENTER_HORIZONTAL);
		if(!header.isRead())
			layout.setBackgroundColor(res.getColor(R.color.unread_background));

		LinearLayout headerLayout = new LinearLayout(ctx);
		headerLayout.setOrientation(HORIZONTAL);
		headerLayout.setGravity(CENTER_VERTICAL);

		AuthorView authorView = new AuthorView(ctx);
		authorView.setLayoutParams(WRAP_WRAP_1);
		Author author = header.getAuthor();
		if(author == null) authorView.init(null, header.getAuthorStatus());
		else authorView.init(author.getName(), header.getAuthorStatus());
		headerLayout.addView(authorView);

		TextView date = new TextView(ctx);
		date.setPadding(0, pad, pad, pad);
		long timestamp = header.getTimestamp();
		date.setText(DateUtils.getRelativeTimeSpanString(ctx, timestamp));
		headerLayout.addView(date);
		layout.addView(headerLayout);

		if(item.getBody() == null) {
			TextView ellipsis = new TextView(ctx);
			ellipsis.setPadding(pad, 0, pad, pad);
			ellipsis.setText("\u2026");
			layout.addView(ellipsis);
		} else if(header.getContentType().equals("text/plain")) {
			TextView text = new TextView(ctx);
			text.setPadding(pad, 0, pad, pad);
			text.setText(StringUtils.fromUtf8(item.getBody()));
			layout.addView(text);
		} else {
			ImageButton attachment = new ImageButton(ctx);
			attachment.setPadding(pad, 0, pad, pad);
			attachment.setImageResource(R.drawable.content_attachment);
			layout.addView(attachment);
		}

		return layout;
	}
}