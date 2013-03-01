package net.sf.briar.android.messages;

import static android.graphics.Typeface.BOLD;
import static android.view.Gravity.CENTER;
import static android.view.Gravity.LEFT;
import static android.view.ViewGroup.LayoutParams.WRAP_CONTENT;
import static android.widget.LinearLayout.HORIZONTAL;
import static android.widget.LinearLayout.VERTICAL;
import static java.text.DateFormat.SHORT;

import java.util.ArrayList;

import net.sf.briar.R;
import android.content.Context;
import android.text.format.DateUtils;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.LinearLayout.LayoutParams;
import android.widget.TextView;

class ConversationListAdapter extends ArrayAdapter<ConversationListItem> {

	ConversationListAdapter(Context ctx) {
		super(ctx, android.R.layout.simple_expandable_list_item_1,
				new ArrayList<ConversationListItem>());
	}

	@Override
	public View getView(int position, View convertView, ViewGroup parent) {
		ConversationListItem item = getItem(position);
		Context ctx = getContext();
		LinearLayout layout = new LinearLayout(ctx);
		layout.setOrientation(HORIZONTAL);
		layout.setGravity(CENTER);

		ImageView star = new ImageView(ctx);
		star.setPadding(5, 5, 5, 5);
		if(item.getStarred())
			star.setImageResource(R.drawable.rating_important);
		else star.setImageResource(R.drawable.rating_not_important);
		layout.addView(star);

		LinearLayout innerLayout = new LinearLayout(ctx);
		// Give me all the unused width
		innerLayout.setLayoutParams(new LayoutParams(WRAP_CONTENT,
				WRAP_CONTENT, 1));
		innerLayout.setOrientation(VERTICAL);
		innerLayout.setGravity(LEFT);
		innerLayout.setPadding(0, 5, 0, 5);

		TextView name = new TextView(ctx);
		name.setTextSize(18);
		name.setText(item.getName() + " (" + item.getLength() + ")");
		innerLayout.addView(name);

		TextView subject = new TextView(ctx);
		subject.setTextSize(14);
		if(!item.getRead()) subject.setTypeface(null, BOLD);
		subject.setText(item.getSubject());
		innerLayout.addView(subject);
		layout.addView(innerLayout);

		TextView date = new TextView(ctx);
		date.setTextSize(14);
		date.setPadding(5, 0, 10, 0);
		long then = item.getTimestamp(), now = System.currentTimeMillis();
		date.setText(DateUtils.formatSameDayTime(then, now, SHORT, SHORT));
		layout.addView(date);

		return layout;
	}
}
