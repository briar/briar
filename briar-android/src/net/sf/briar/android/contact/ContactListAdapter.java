package net.sf.briar.android.contact;

import static android.view.Gravity.CENTER;
import static android.view.ViewGroup.LayoutParams.WRAP_CONTENT;
import static android.widget.LinearLayout.HORIZONTAL;

import java.util.ArrayList;

import net.sf.briar.R;
import android.content.Context;
import android.content.res.Resources;
import android.text.Html;
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

class ContactListAdapter extends ArrayAdapter<ContactListItem>
implements OnItemClickListener {

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
		layout.setGravity(CENTER);

		ImageView bulb = new ImageView(ctx);
		bulb.setPadding(5, 5, 5, 5);
		if(item.isConnected()) bulb.setImageResource(R.drawable.green_bulb);
		else bulb.setImageResource(R.drawable.grey_bulb);
		layout.addView(bulb);

		TextView name = new TextView(ctx);
		// Give me all the unused width
		name.setLayoutParams(new LayoutParams(WRAP_CONTENT, WRAP_CONTENT, 1));
		name.setTextSize(18);
		name.setText(item.getName());
		layout.addView(name);

		TextView connected = new TextView(ctx);
		connected.setTextSize(14);
		connected.setPadding(5, 0, 5, 0);
		if(item.isConnected()) {
			connected.setText(R.string.contact_connected);
		} else {
			Resources res = ctx.getResources();
			String format = res.getString(R.string.contact_last_connected);
			long then = item.getLastConnected();
			CharSequence ago = DateUtils.getRelativeTimeSpanString(then);
			connected.setText(Html.fromHtml(String.format(format, ago)));
		}
		layout.addView(connected);

		return layout;
	}

	public void onItemClick(AdapterView<?> parent, View view, int position,
			long id) {
		// FIXME: Hook this up to an activity
	}
}
