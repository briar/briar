package org.briarproject.android.contact;

import static android.view.Gravity.BOTTOM;
import static android.view.Gravity.LEFT;
import static android.view.View.INVISIBLE;
import static android.widget.LinearLayout.HORIZONTAL;
import static android.widget.LinearLayout.VERTICAL;
import static org.briarproject.android.util.CommonLayoutParams.MATCH_WRAP;

import java.util.ArrayList;

import org.briarproject.R;
import org.briarproject.android.util.ElasticHorizontalSpace;
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
import android.widget.ImageView;
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
		if (header.isLocal()) layout.setPadding(3 * pad, 0, 0, 0);
		else layout.setPadding(0, 0, 3 * pad, 0);

		int background = res.getColor(R.color.private_message_background);

		View content;
		if (item.getBody() == null) {
			TextView ellipsis = new TextView(ctx);
			ellipsis.setText("\u2026");
			content = ellipsis;
		} else if (header.getContentType().equals("text/plain")) {
			TextView text = new TextView(ctx);
			text.setText(StringUtils.fromUtf8(item.getBody()));
			content = text;
		} else {
			ImageButton attachment = new ImageButton(ctx);
			attachment.setImageResource(R.drawable.content_attachment);
			content = attachment;
		}
		content.setLayoutParams(MATCH_WRAP);
		content.setBackgroundColor(background);
		content.setPadding(pad, pad, pad, 0);
		layout.addView(content);

		if (header.isLocal()) {
			LinearLayout footer = new LinearLayout(ctx);
			footer.setLayoutParams(MATCH_WRAP);
			footer.setOrientation(HORIZONTAL);
			footer.setGravity(BOTTOM);
			footer.setPadding(pad, 0, pad, pad);
			footer.setBackgroundColor(background);

			footer.addView(new ElasticHorizontalSpace(ctx));

			ImageView delivered = new ImageView(ctx);
			delivered.setPadding(0, 0, pad, 0);
			delivered.setImageResource(R.drawable.message_delivered);
			if (!item.isDelivered()) delivered.setVisibility(INVISIBLE);
			footer.addView(delivered);

			TextView date = new TextView(ctx);
			date.setTextColor(res.getColor(R.color.private_message_date));
			long timestamp = header.getTimestamp();
			date.setText(DateUtils.getRelativeTimeSpanString(ctx, timestamp));
			footer.addView(date);

			layout.addView(footer);
		} else {
			TextView date = new TextView(ctx);
			date.setLayoutParams(MATCH_WRAP);
			date.setGravity(LEFT);
			date.setTextColor(res.getColor(R.color.private_message_date));
			date.setBackgroundColor(background);
			date.setPadding(pad, 0, pad, pad);
			long timestamp = header.getTimestamp();
			date.setText(DateUtils.getRelativeTimeSpanString(ctx, timestamp));
			layout.addView(date);
		}

		return layout;
	}
}