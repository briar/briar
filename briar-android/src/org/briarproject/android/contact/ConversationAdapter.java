package org.briarproject.android.contact;

import static android.view.Gravity.LEFT;
import static android.view.Gravity.RIGHT;
import static android.widget.RelativeLayout.ALIGN_PARENT_LEFT;
import static android.widget.RelativeLayout.ALIGN_PARENT_RIGHT;
import static android.widget.RelativeLayout.ALIGN_PARENT_TOP;
import static android.widget.RelativeLayout.BELOW;
import static android.widget.RelativeLayout.LEFT_OF;
import static android.widget.RelativeLayout.RIGHT_OF;
import static java.text.DateFormat.SHORT;

import java.util.ArrayList;

import org.briarproject.R;
import org.briarproject.android.util.CommonLayoutParams;
import org.briarproject.android.util.LayoutUtils;
import org.briarproject.api.db.MessageHeader;
import org.briarproject.util.StringUtils;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;
import android.text.format.DateUtils;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.RelativeLayout;
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

		int background;
		if(header.isRead()) background = res.getColor(R.color.read_background);
		else background = res.getColor(R.color.unread_background);

		TextView date = new TextView(ctx);
		date.setId(1);
		date.setTextSize(14);
		date.setBackgroundColor(background);
		date.setPadding(pad, pad, pad, 0);
		long then = header.getTimestamp(), now = System.currentTimeMillis();
		date.setText(DateUtils.formatSameDayTime(then, now, SHORT, SHORT));

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
		content.setBackgroundColor(background);
		content.setPadding(pad, 0, pad, pad);

		ImageView bubble = new ImageView(ctx);
		bubble.setId(3);

		RelativeLayout layout = new RelativeLayout(ctx);
		if(header.isLocal()) {
			Drawable d;
			if(header.isRead())
				d = res.getDrawable(R.drawable.bubble_read_right);
			else d = res.getDrawable(R.drawable.bubble_unread_right);
			bubble.setImageDrawable(d);
			layout.setPadding(d.getIntrinsicWidth(), 0, 0, 0);
			// Bubble tip and date at the top right, content below
			date.setGravity(RIGHT);
			RelativeLayout.LayoutParams topRight =
					CommonLayoutParams.relative();
			topRight.addRule(ALIGN_PARENT_TOP);
			topRight.addRule(ALIGN_PARENT_RIGHT);
			layout.addView(bubble, topRight);
			RelativeLayout.LayoutParams leftOf = CommonLayoutParams.relative();
			leftOf.addRule(ALIGN_PARENT_TOP);
			leftOf.addRule(ALIGN_PARENT_LEFT);
			leftOf.addRule(LEFT_OF, 3);
			layout.addView(date, leftOf);
			RelativeLayout.LayoutParams below = CommonLayoutParams.relative();
			below.addRule(ALIGN_PARENT_LEFT);
			below.addRule(LEFT_OF, 3);
			below.addRule(BELOW, 1);
			layout.addView(content, below);
		} else {
			Drawable d;
			if(header.isRead())
				d = res.getDrawable(R.drawable.bubble_read_left);
			else d = res.getDrawable(R.drawable.bubble_unread_left);
			bubble.setImageDrawable(d);
			layout.setPadding(0, 0, d.getIntrinsicWidth(), 0);
			// Bubble tip and date at the top left, content below
			date.setGravity(LEFT);
			RelativeLayout.LayoutParams topLeft = CommonLayoutParams.relative();
			topLeft.addRule(ALIGN_PARENT_TOP);
			topLeft.addRule(ALIGN_PARENT_LEFT);
			layout.addView(bubble, topLeft);
			RelativeLayout.LayoutParams rightOf = CommonLayoutParams.relative();
			rightOf.addRule(ALIGN_PARENT_TOP);
			rightOf.addRule(ALIGN_PARENT_RIGHT);
			rightOf.addRule(RIGHT_OF, 3);
			layout.addView(date, rightOf);
			RelativeLayout.LayoutParams below = CommonLayoutParams.relative();
			below.addRule(ALIGN_PARENT_RIGHT);
			below.addRule(RIGHT_OF, 3);
			below.addRule(BELOW, 1);
			layout.addView(content, below);
		}
		return layout;
	}
}