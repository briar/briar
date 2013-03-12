package net.sf.briar.android.groups;

import static android.graphics.Typeface.BOLD;
import static android.view.Gravity.CENTER_VERTICAL;
import static android.widget.LinearLayout.HORIZONTAL;
import static android.widget.LinearLayout.VERTICAL;
import static java.text.DateFormat.SHORT;
import static net.sf.briar.api.Rating.BAD;
import static net.sf.briar.api.Rating.GOOD;

import java.util.ArrayList;

import net.sf.briar.R;
import net.sf.briar.android.widgets.CommonLayoutParams;
import net.sf.briar.android.widgets.HorizontalSpace;
import net.sf.briar.api.Rating;
import net.sf.briar.api.messaging.Author;
import android.content.Context;
import android.content.res.Resources;
import android.text.format.DateUtils;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

class GroupAdapter extends ArrayAdapter<GroupItem> {

	GroupAdapter(Context ctx) {
		super(ctx, android.R.layout.simple_expandable_list_item_1,
				new ArrayList<GroupItem>());
	}

	@Override
	public View getView(int position, View convertView, ViewGroup parent) {
		GroupItem item = getItem(position);
		Context ctx = getContext();
		// FIXME: Use a RelativeLayout
		LinearLayout layout = new LinearLayout(ctx);
		layout.setOrientation(HORIZONTAL);
		layout.setGravity(CENTER_VERTICAL);

		LinearLayout innerLayout = new LinearLayout(ctx);
		// Give me all the unused width
		innerLayout.setLayoutParams(CommonLayoutParams.WRAP_WRAP_1);
		innerLayout.setOrientation(VERTICAL);

		LinearLayout innerInnerLayout = new LinearLayout(ctx);
		innerInnerLayout.setOrientation(HORIZONTAL);
		innerInnerLayout.setGravity(CENTER_VERTICAL);

		Rating rating = item.getRating();
		if(rating == GOOD) {
			ImageView good = new ImageView(ctx);
			good.setPadding(0, 10, 10, 10);
			good.setImageResource(R.drawable.rating_good);
			innerInnerLayout.addView(good);
		} else if(rating == BAD) {
			ImageView bad = new ImageView(ctx);
			bad.setPadding(0, 10, 10, 10);
			bad.setImageResource(R.drawable.rating_bad);
			innerInnerLayout.addView(bad);
		}

		TextView name = new TextView(ctx);
		name.setTextSize(18);
		name.setMaxLines(1);
		name.setPadding(10, 10, 10, 10);
		Author author = item.getAuthor();
		Resources res = ctx.getResources();
		if(author == null) {
			name.setTextColor(res.getColor(R.color.anonymous_author));
			name.setText(R.string.anonymous);
		} else {
			name.setTextColor(res.getColor(R.color.pseudonymous_author));
			name.setText(author.getName());
		}
		innerInnerLayout.addView(name);
		innerLayout.addView(innerInnerLayout);

		if(item.getContentType().equals("text/plain")) {
			TextView subject = new TextView(ctx);
			subject.setTextSize(14);
			subject.setMaxLines(2);
			subject.setPadding(10, 0, 10, 10);
			if(!item.isRead()) subject.setTypeface(null, BOLD);
			subject.setText(item.getSubject());
			innerLayout.addView(subject);
		} else {
			ImageView attachment = new ImageView(ctx);
			attachment.setPadding(10, 0, 10, 10);
			attachment.setImageResource(R.drawable.content_attachment);
			innerInnerLayout.addView(attachment);
		}
		innerInnerLayout.addView(new HorizontalSpace(ctx));
		layout.addView(innerLayout);

		TextView date = new TextView(ctx);
		date.setTextSize(14);
		date.setPadding(0, 10, 10, 10);
		long then = item.getTimestamp(), now = System.currentTimeMillis();
		date.setText(DateUtils.formatSameDayTime(then, now, SHORT, SHORT));
		layout.addView(date);

		return layout;
	}
}
