package net.sf.briar.android.messages;

import static android.graphics.Typeface.BOLD;
import static android.view.Gravity.CENTER;
import static android.view.ViewGroup.LayoutParams.WRAP_CONTENT;
import static android.widget.LinearLayout.HORIZONTAL;
import static java.text.DateFormat.SHORT;

import java.util.ArrayList;

import net.sf.briar.R;
import net.sf.briar.api.db.PrivateMessageHeader;
import android.content.Context;
import android.text.format.DateUtils;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.LinearLayout.LayoutParams;
import android.widget.TextView;

class ConversationAdapter extends ArrayAdapter<PrivateMessageHeader>
implements OnItemClickListener {

	ConversationAdapter(Context ctx) {
		super(ctx, android.R.layout.simple_expandable_list_item_1,
				new ArrayList<PrivateMessageHeader>());
	}

	@Override
	public View getView(int position, View convertView, ViewGroup parent) {
		PrivateMessageHeader item = getItem(position);
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

		TextView subject = new TextView(ctx);
		// Give me all the unused width
		subject.setLayoutParams(new LayoutParams(WRAP_CONTENT, WRAP_CONTENT,
				1));
		subject.setTextSize(14);
		if(!item.getRead()) subject.setTypeface(null, BOLD);
		subject.setText(item.getSubject());
		layout.addView(subject);

		TextView date = new TextView(ctx);
		date.setTextSize(14);
		date.setPadding(10, 0, 10, 0);
		long then = item.getTimestamp(), now = System.currentTimeMillis();
		date.setText(DateUtils.formatSameDayTime(then, now, SHORT, SHORT));
		layout.addView(date);

		return layout;
	}

	public void onItemClick(AdapterView<?> parent, View view, int position,
			long id) {
		// FIXME
	}
}
