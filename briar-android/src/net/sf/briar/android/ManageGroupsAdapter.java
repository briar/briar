package net.sf.briar.android;

import static android.view.View.INVISIBLE;
import static android.widget.LinearLayout.HORIZONTAL;
import static android.widget.LinearLayout.VERTICAL;

import java.util.ArrayList;

import net.sf.briar.R;
import net.sf.briar.api.messaging.GroupStatus;
import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListAdapter;
import android.widget.TextView;

public class ManageGroupsAdapter extends ArrayAdapter<GroupStatus>
implements ListAdapter {

	public ManageGroupsAdapter(Context ctx) {
		super(ctx, android.R.layout.simple_expandable_list_item_1,
				new ArrayList<GroupStatus>());
	}

	@Override
	public View getView(int position, View convertView, ViewGroup parent) {
		final GroupStatus item = getItem(position);
		Context ctx = getContext();

		LinearLayout layout = new LinearLayout(ctx);
		layout.setOrientation(HORIZONTAL);

		ImageView subscribed = new ImageView(ctx);
		subscribed.setPadding(5, 5, 5, 5);
		subscribed.setImageResource(R.drawable.navigation_accept);
		if(!item.isSubscribed()) subscribed.setVisibility(INVISIBLE);
		layout.addView(subscribed);

		LinearLayout innerLayout = new LinearLayout(ctx);
		innerLayout.setOrientation(VERTICAL);

		TextView name = new TextView(ctx);
		name.setTextSize(18);
		name.setMaxLines(1);
		name.setPadding(0, 10, 10, 10);
		name.setText(item.getGroup().getName());
		innerLayout.addView(name);

		TextView status = new TextView(ctx);
		status.setTextSize(14);
		status.setPadding(0, 0, 10, 10);
		if(item.isSubscribed()) {
			if(item.isVisibleToAll()) status.setText(R.string.subscribed_all);
			else status.setText(R.string.subscribed_some);
		} else {
			status.setText(R.string.not_subscribed);
		}
		innerLayout.addView(status);
		layout.addView(innerLayout);

		return layout;
	}
}
