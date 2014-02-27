package org.briarproject.android.contact;

import static android.view.Gravity.LEFT;
import static android.view.Gravity.RIGHT;
import static android.widget.LinearLayout.VERTICAL;
import static org.briarproject.android.util.CommonLayoutParams.MATCH_WRAP;

import java.util.ArrayList;

import org.briarproject.R;
import org.briarproject.android.util.LayoutUtils;
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

		LinearLayout layout = new LinearLayout(ctx);
		layout.setOrientation(VERTICAL);
		if(header.isLocal()) layout.setPadding(3 * pad, 0, 0, 0);
		else layout.setPadding(0, 0, 3 * pad, 0);

		int background = res.getColor(R.color.private_message_background);

		View content;
		if(item.getBody() == null) {
			TextView ellipsis = new TextView(ctx);
			ellipsis.setText("\u2026");
			content = ellipsis;
		} else if(header.getContentType().equals("text/plain")) {
			TextView text = new TextView(ctx);
			text.setText(StringUtils.fromUtf8(item.getBody()));
			content = text;
		} else {
			ImageButton attachment = new ImageButton(ctx);
			attachment.setImageResource(R.drawable.content_attachment);
			content = attachment;
		}
		content.setId(2);
		content.setLayoutParams(MATCH_WRAP);
		content.setBackgroundColor(background);
		content.setPadding(pad, pad, pad, 0);
		layout.addView(content);

		TextView date = new TextView(ctx);
		date.setId(1);
		date.setLayoutParams(MATCH_WRAP);
		if(header.isLocal()) date.setGravity(RIGHT);
		else date.setGravity(LEFT);
		date.setTextSize(14);
		date.setTextColor(res.getColor(R.color.private_message_date));
		date.setBackgroundColor(background);
		date.setPadding(pad, 0, pad, pad);
		long timestamp = header.getTimestamp();
		date.setText(DateUtils.getRelativeTimeSpanString(ctx, timestamp));
		layout.addView(date);

		return layout;
	}
}