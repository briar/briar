package net.sf.briar.android.contact;

import static android.view.Gravity.CENTER;
import static android.view.ViewGroup.LayoutParams.WRAP_CONTENT;
import static android.widget.LinearLayout.HORIZONTAL;

import java.util.ArrayList;

import net.sf.briar.R;
import android.content.Context;
import android.text.Html;
import android.text.format.DateUtils;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.LinearLayout.LayoutParams;
import android.widget.TextView;

public class ContactListAdapter extends ArrayAdapter<ContactListItem> {

	public ContactListAdapter(Context context) {
		super(context, android.R.layout.simple_expandable_list_item_1,
				new ArrayList<ContactListItem>());
	}

	@Override
	public View getView(int position, View convertView, ViewGroup parent) {
		ContactListItem item = getItem(position);
		Context ctx = getContext();
		LinearLayout layout = new LinearLayout(ctx);
		layout.setOrientation(HORIZONTAL);
		layout.setGravity(CENTER);

		ImageView bulb = new ImageView(ctx);
		if(item.getConnected()) bulb.setImageResource(R.drawable.green_bulb);
		else bulb.setImageResource(R.drawable.grey_bulb);
		bulb.setPadding(5, 0, 5, 0);
		layout.addView(bulb);

		TextView name = new TextView(ctx);
		name.setLayoutParams(new LayoutParams(WRAP_CONTENT, WRAP_CONTENT, 1f));
		name.setTextSize(18);
		name.setText(item.getName());
		layout.addView(name);

		TextView connected = new TextView(ctx);
		connected.setTextSize(12);
		connected.setPadding(5, 0, 5, 0);
		if(item.getConnected()) {
			connected.setText(R.string.contact_connected);
		} else {
			String format = ctx.getResources().getString(
					R.string.contact_last_connected);
			long then = item.getLastConnected();
			CharSequence ago = DateUtils.getRelativeTimeSpanString(then);
			connected.setText(Html.fromHtml(String.format(format, ago)));
		}
		layout.addView(connected);

		return layout;
	}
}
