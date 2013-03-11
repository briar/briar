package net.sf.briar.android.messages;

import static android.graphics.Typeface.BOLD;
import static android.view.Gravity.CENTER_VERTICAL;
import static android.view.Gravity.LEFT;
import static android.widget.LinearLayout.HORIZONTAL;
import static android.widget.LinearLayout.VERTICAL;
import static java.text.DateFormat.SHORT;

import java.util.ArrayList;

import net.sf.briar.android.widgets.CommonLayoutParams;
import android.content.Context;
import android.content.Intent;
import android.text.format.DateUtils;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ArrayAdapter;
import android.widget.LinearLayout;
import android.widget.TextView;

class ConversationListAdapter extends ArrayAdapter<ConversationListItem>
implements OnItemClickListener {

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
		layout.setGravity(CENTER_VERTICAL);

		LinearLayout innerLayout = new LinearLayout(ctx);
		// Give me all the unused width
		innerLayout.setLayoutParams(CommonLayoutParams.WRAP_WRAP_1);
		innerLayout.setOrientation(VERTICAL);
		innerLayout.setGravity(LEFT);

		TextView name = new TextView(ctx);
		name.setTextSize(18);
		name.setPadding(10, 10, 10, 0);
		name.setText(item.getName() + " (" + item.getLength() + ")");
		innerLayout.addView(name);

		TextView subject = new TextView(ctx);
		subject.setTextSize(14);
		subject.setMaxLines(2);
		subject.setPadding(10, 0, 10, 10);
		if(!item.isRead()) subject.setTypeface(null, BOLD);
		subject.setText(item.getSubject());
		innerLayout.addView(subject);
		layout.addView(innerLayout);

		TextView date = new TextView(ctx);
		date.setTextSize(14);
		date.setPadding(0, 10, 10, 10);
		long then = item.getTimestamp(), now = System.currentTimeMillis();
		date.setText(DateUtils.formatSameDayTime(then, now, SHORT, SHORT));
		layout.addView(date);

		return layout;
	}

	public void onItemClick(AdapterView<?> parent, View view, int position,
			long id) {
		ConversationListItem item = getItem(position);
		Intent i = new Intent(getContext(), ConversationActivity.class);
		i.putExtra("net.sf.briar.CONTACT_ID", item.getContactId().getInt());
		i.putExtra("net.sf.briar.CONTACT_NAME", item.getName());
		getContext().startActivity(i);
	}
}
