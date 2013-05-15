package net.sf.briar.android.contact;

import static android.view.Gravity.CENTER_VERTICAL;
import static android.widget.LinearLayout.HORIZONTAL;
import static net.sf.briar.android.util.CommonLayoutParams.WRAP_WRAP_1;

import java.util.ArrayList;

import net.sf.briar.R;
import android.content.Context;
import android.content.res.Resources;
import android.text.Html;
import android.text.format.DateUtils;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

class ContactListAdapter extends ArrayAdapter<ContactListItem> {

	ContactListAdapter(Context ctx) {
		super(ctx, android.R.layout.simple_expandable_list_item_1,
				new ArrayList<ContactListItem>());
	}

	@Override
	public View getView(int position, View convertView, ViewGroup parent) {
		ContactListItem item = getItem(position);
		Context ctx = getContext();
		LinearLayout layout = new LinearLayout(ctx);
		layout.setOrientation(HORIZONTAL);
		layout.setGravity(CENTER_VERTICAL);

		ImageView bulb = new ImageView(ctx);
		bulb.setPadding(5, 5, 5, 5);
		if(item.isConnected())
			bulb.setImageResource(R.drawable.contact_connected);
		else bulb.setImageResource(R.drawable.contact_disconnected);
		layout.addView(bulb);

		TextView name = new TextView(ctx);
		// Give me all the unused width
		name.setLayoutParams(WRAP_WRAP_1);
		name.setTextSize(18);
		name.setMaxLines(1);
		name.setPadding(0, 10, 10, 10);
		name.setText(item.getContactName());
		layout.addView(name);

		TextView connected = new TextView(ctx);
		connected.setTextSize(14);
		connected.setPadding(0, 10, 10, 10);
		if(item.isConnected()) {
			connected.setText(R.string.contact_connected);
		} else {
			Resources res = ctx.getResources();
			String format = res.getString(R.string.format_last_connected);
			long then = item.getLastConnected();
			CharSequence ago = DateUtils.getRelativeTimeSpanString(then);
			connected.setText(Html.fromHtml(String.format(format, ago)));
		}
		layout.addView(connected);

		return layout;
	}
}
