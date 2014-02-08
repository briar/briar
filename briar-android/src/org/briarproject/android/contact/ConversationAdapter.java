package org.briarproject.android.contact;

import static android.view.Gravity.CENTER_HORIZONTAL;
import static android.view.Gravity.CENTER_VERTICAL;
import static android.widget.LinearLayout.HORIZONTAL;
import static android.widget.LinearLayout.VERTICAL;
import static java.text.DateFormat.SHORT;
import static org.briarproject.android.util.CommonLayoutParams.WRAP_WRAP_1;
import static org.briarproject.api.Author.Status.VERIFIED;

import java.util.ArrayList;

import org.briarproject.R;
import org.briarproject.android.util.AuthorView;
import org.briarproject.android.util.LayoutUtils;
import org.briarproject.api.db.MessageHeader;
import org.briarproject.util.StringUtils;

import android.content.Context;
import android.content.res.Resources;
import android.text.format.DateUtils;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

class ConversationAdapter extends ArrayAdapter<ConversationItem> {

	private final int pad;

	ConversationAdapter(Context ctx) {
		super(ctx, android.R.layout.simple_expandable_list_item_1,
				new ArrayList<ConversationItem>());
		pad = LayoutUtils.getPadding(ctx);
	}

	@Override
	public View getView(int position, View convertView, ViewGroup parent) {
		ConversationItem item = getItem(position);
		MessageHeader header = item.getHeader();
		Context ctx = getContext();
		Resources res = ctx.getResources();

		LinearLayout headerLayout = new LinearLayout(ctx);
		headerLayout.setOrientation(HORIZONTAL);
		headerLayout.setGravity(CENTER_VERTICAL);
		int background;
		if(header.isRead()) background = res.getColor(R.color.read_background);
		else background = res.getColor(R.color.unread_background);
		headerLayout.setBackgroundColor(background);

		AuthorView authorView = new AuthorView(ctx);
		authorView.setLayoutParams(WRAP_WRAP_1);
		authorView.init(header.getAuthor().getName(), VERIFIED);
		headerLayout.addView(authorView);

		// FIXME: Factor this out into a TimestampView
		TextView date = new TextView(ctx);
		date.setTextSize(14);
		date.setPadding(0, pad, pad, pad);
		long then = header.getTimestamp(), now = System.currentTimeMillis();
		date.setText(DateUtils.formatSameDayTime(then, now, SHORT, SHORT));
		headerLayout.addView(date);

		if(!item.isExpanded()) return headerLayout;

		LinearLayout expanded = new LinearLayout(ctx);
		expanded.setOrientation(VERTICAL);
		expanded.setGravity(CENTER_HORIZONTAL);
		expanded.setBackgroundColor(background);
		expanded.addView(headerLayout);

		byte[] body = item.getBody();
		if(body == null) {
			ProgressBar progress = new ProgressBar(ctx);
			progress.setPadding(pad, 0, pad, pad);
			progress.setIndeterminate(true);
			expanded.addView(progress);
		} else if(header.getContentType().equals("text/plain")) {
			TextView text = new TextView(ctx);
			text.setPadding(pad, 0, pad, pad);
			text.setBackgroundColor(background);
			text.setText(StringUtils.fromUtf8(body));
			expanded.addView(text);
		}

		return expanded;
	}
}