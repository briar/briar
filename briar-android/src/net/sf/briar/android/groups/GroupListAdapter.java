package net.sf.briar.android.groups;

import static android.graphics.Typeface.BOLD;
import static android.view.Gravity.CENTER;
import static android.widget.LinearLayout.HORIZONTAL;
import static android.widget.LinearLayout.VERTICAL;
import static java.text.DateFormat.SHORT;
import static net.sf.briar.android.groups.GroupListItem.MANAGE;
import static net.sf.briar.android.util.CommonLayoutParams.WRAP_WRAP_1;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import net.sf.briar.R;
import net.sf.briar.android.util.HorizontalSpace;
import android.content.Context;
import android.content.res.Resources;
import android.text.format.DateUtils;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

class GroupListAdapter extends BaseAdapter {

	private final Context ctx;
	private final List<GroupListItem> list = new ArrayList<GroupListItem>();
	private int available = 0;

	GroupListAdapter(Context ctx) {
		this.ctx = ctx;
	}

	public void setAvailable(int available) {
		this.available = available;
	}

	public void add(GroupListItem item) {
		list.add(item);
	}

	public void clear() {
		list.clear();
	}

	public int getCount() {
		return available == 0 ? list.size() : list.size() + 1;
	}

	public GroupListItem getItem(int position) {
		return position == list.size() ? MANAGE : list.get(position);
	}

	public long getItemId(int position) {
		return android.R.layout.simple_expandable_list_item_1;
	}

	public View getView(int position, View convertView, ViewGroup parent) {
		GroupListItem item = getItem(position);
		Resources res = ctx.getResources();

		if(item == MANAGE) {
			TextView manage = new TextView(ctx);
			manage.setGravity(CENTER);
			manage.setTextSize(18);
			manage.setPadding(10, 10, 10, 10);
			String format = res.getQuantityString(R.plurals.forums_available,
					available);
			manage.setText(String.format(format, available));
			return manage;
		}

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
		String groupName = item.getGroup().getName();
		if(unread > 0) name.setText(groupName + " (" + unread + ")");
		else name.setText(groupName);
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

	@Override
	public boolean isEmpty() {
		return list.isEmpty() && available == 0;
	}

	public void remove(GroupListItem item) {
		list.remove(item);
	}

	public void sort(Comparator<GroupListItem> comparator) {
		Collections.sort(list, comparator);
	}
}
