package org.briarproject.android.contact;

import static android.text.TextUtils.TruncateAt.END;
import static android.view.Gravity.CENTER_VERTICAL;
import static android.widget.LinearLayout.HORIZONTAL;
import static org.briarproject.android.util.CommonLayoutParams.WRAP_WRAP_1;

import java.util.ArrayList;

import org.briarproject.R;
import org.briarproject.android.util.LayoutUtils;

import android.content.Context;
import android.content.res.Resources;
import android.text.format.DateUtils;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

class ContactListAdapter extends ArrayAdapter<ContactListItem> {

	private final int pad;

	ContactListAdapter(Context ctx) {
		super(ctx, android.R.layout.simple_expandable_list_item_1,
				new ArrayList<ContactListItem>());
		pad = LayoutUtils.getPadding(ctx);
	}

	@Override
	public View getView(int position, View convertView, ViewGroup parent) {
		ContactListItem item = getItem(position);
		Context ctx = getContext();
		Resources res = ctx.getResources();

		LinearLayout layout = new LinearLayout(ctx);
		layout.setOrientation(HORIZONTAL);
		layout.setGravity(CENTER_VERTICAL);
		int unread = item.getUnreadCount();
		if(unread > 0)
			layout.setBackgroundColor(res.getColor(R.color.unread_background));

		ImageView bulb = new ImageView(ctx);
		bulb.setPadding(pad, pad, pad, pad);
		if(item.isConnected())
			bulb.setImageResource(R.drawable.contact_connected);
		else bulb.setImageResource(R.drawable.contact_disconnected);
		layout.addView(bulb);

		TextView name = new TextView(ctx);
		name.setLayoutParams(WRAP_WRAP_1);
		name.setTextSize(18);
		name.setSingleLine();
		name.setEllipsize(END);
		name.setPadding(0, pad, pad, pad);
		String contactName = item.getContact().getAuthor().getName();
		if(unread > 0) name.setText(contactName + " (" + unread + ")");
		else name.setText(contactName);
		layout.addView(name);

		if(item.isEmpty()) {
			TextView noMessages = new TextView(ctx);
			noMessages.setPadding(pad, pad, pad, pad);
			noMessages.setTextColor(res.getColor(R.color.no_private_messages));
			noMessages.setText(R.string.no_private_messages);
			layout.addView(noMessages);
		} else {
			TextView date = new TextView(ctx);
			date.setPadding(pad, pad, pad, pad);
			long timestamp = item.getTimestamp();
			date.setText(DateUtils.getRelativeTimeSpanString(ctx, timestamp));
			layout.addView(date);
		}

		return layout;
	}
}
