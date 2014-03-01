package org.briarproject.android.groups;

import static android.text.TextUtils.TruncateAt.END;
import static android.view.View.INVISIBLE;
import static android.widget.LinearLayout.HORIZONTAL;
import static android.widget.LinearLayout.VERTICAL;

import java.util.ArrayList;

import org.briarproject.R;
import org.briarproject.android.util.LayoutUtils;
import org.briarproject.api.messaging.GroupStatus;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

class ManageGroupsAdapter extends ArrayAdapter<ManageGroupsItem> {

	private final int pad;

	ManageGroupsAdapter(Context ctx) {
		super(ctx, android.R.layout.simple_expandable_list_item_1,
				new ArrayList<ManageGroupsItem>());
		pad = LayoutUtils.getPadding(ctx);
	}

	@Override
	public View getView(int position, View convertView, ViewGroup parent) {
		ManageGroupsItem item = getItem(position);
		GroupStatus groupStatus = item.getGroupStatus();
		Context ctx = getContext();

		LinearLayout layout = new LinearLayout(ctx);
		layout.setOrientation(HORIZONTAL);

		ImageView subscribed = new ImageView(ctx);
		subscribed.setPadding(pad, pad, pad, pad);
		subscribed.setImageResource(R.drawable.navigation_accept);
		if(!groupStatus.isSubscribed()) subscribed.setVisibility(INVISIBLE);
		layout.addView(subscribed);

		LinearLayout innerLayout = new LinearLayout(ctx);
		innerLayout.setOrientation(VERTICAL);

		TextView name = new TextView(ctx);
		name.setTextSize(18);
		name.setSingleLine();
		name.setEllipsize(END);
		name.setPadding(0, pad, pad, pad);
		name.setText(groupStatus.getGroup().getName());
		innerLayout.addView(name);

		TextView status = new TextView(ctx);
		status.setPadding(0, 0, pad, pad);
		if(groupStatus.isSubscribed()) {
			if(groupStatus.isVisibleToAll())
				status.setText(R.string.subscribed_all);
			else status.setText(R.string.subscribed_some);
		} else {
			status.setText(R.string.not_subscribed);
		}
		innerLayout.addView(status);
		layout.addView(innerLayout);

		return layout;
	}
}
