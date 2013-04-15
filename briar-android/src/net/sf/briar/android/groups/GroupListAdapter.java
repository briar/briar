package net.sf.briar.android.groups;

import static android.graphics.Typeface.BOLD;
import static android.widget.LinearLayout.HORIZONTAL;
import static android.widget.LinearLayout.VERTICAL;
import static java.text.DateFormat.SHORT;
import static net.sf.briar.android.widgets.CommonLayoutParams.WRAP_WRAP_1;

import java.util.ArrayList;

import net.sf.briar.R;
import net.sf.briar.android.widgets.HorizontalSpace;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.text.format.DateUtils;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

class GroupListAdapter extends ArrayAdapter<GroupListItem>
implements OnItemClickListener {

	GroupListAdapter(Context ctx) {
		super(ctx, android.R.layout.simple_expandable_list_item_1,
				new ArrayList<GroupListItem>());
	}

	@Override
	public View getView(int position, View convertView, ViewGroup parent) {
		GroupListItem item = getItem(position);
		Context ctx = getContext();
		Resources res = ctx.getResources();

		LinearLayout layout = new LinearLayout(ctx);
		layout.setOrientation(HORIZONTAL);
		if(item.getUnreadCount() > 0)
			layout.setBackgroundColor(res.getColor(R.color.unread_background));

		LinearLayout innerLayout = new LinearLayout(ctx);
		// Give me all the unused width
		innerLayout.setLayoutParams(WRAP_WRAP_1);
		innerLayout.setOrientation(VERTICAL);

		TextView name = new TextView(ctx);
		name.setTextSize(18);
		name.setMaxLines(1);
		name.setPadding(10, 10, 10, 10);
		int unread = item.getUnreadCount();
		if(unread > 0) name.setText(item.getGroupName() + " (" + unread + ")");
		else name.setText(item.getGroupName());
		innerLayout.addView(name);

		if(item.isEmpty()) {
			TextView noPosts = new TextView(ctx);
			noPosts.setTextSize(14);
			noPosts.setPadding(10, 0, 10, 10);
			noPosts.setTextColor(res.getColor(R.color.no_posts));
			noPosts.setText(R.string.no_posts);
			innerLayout.addView(noPosts);
			layout.addView(innerLayout);
		} else {
			if(item.getContentType().equals("text/plain")) {
				TextView subject = new TextView(ctx);
				subject.setTextSize(14);
				subject.setMaxLines(2);
				subject.setPadding(10, 0, 10, 10);
				if(item.getUnreadCount() > 0) subject.setTypeface(null, BOLD);
				String s = item.getSubject();
				subject.setText(s == null ? "" : s);
				innerLayout.addView(subject);
			} else {
				LinearLayout attachmentLayout = new LinearLayout(ctx);
				attachmentLayout.setOrientation(HORIZONTAL);
				ImageView attachment = new ImageView(ctx);
				attachment.setPadding(5, 0, 5, 5);
				attachment.setImageResource(R.drawable.content_attachment);
				attachmentLayout.addView(attachment);
				attachmentLayout.addView(new HorizontalSpace(ctx));
				innerLayout.addView(attachmentLayout);
			}
			layout.addView(innerLayout);

			TextView date = new TextView(ctx);
			date.setTextSize(14);
			date.setPadding(0, 10, 10, 10);
			long then = item.getTimestamp(), now = System.currentTimeMillis();
			date.setText(DateUtils.formatSameDayTime(then, now, SHORT, SHORT));
			layout.addView(date);
		}

		return layout;
	}

	public void onItemClick(AdapterView<?> parent, View view, int position,
			long id) {
		GroupListItem item = getItem(position);
		Intent i = new Intent(getContext(), GroupActivity.class);
		i.putExtra("net.sf.briar.GROUP_ID", item.getGroupId().getBytes());
		i.putExtra("net.sf.briar.GROUP_NAME", item.getGroupName());
		getContext().startActivity(i);
	}
}
