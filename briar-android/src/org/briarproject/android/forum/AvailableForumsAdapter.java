package org.briarproject.android.forum;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.briarproject.R;
import org.briarproject.android.util.LayoutUtils;
import org.briarproject.api.contact.Contact;
import org.briarproject.util.StringUtils;

import java.util.ArrayList;
import java.util.Collection;

import static android.text.TextUtils.TruncateAt.END;
import static android.widget.LinearLayout.VERTICAL;

class AvailableForumsAdapter extends ArrayAdapter<AvailableForumsItem> {

	private final int pad;

	AvailableForumsAdapter(Context ctx) {
		super(ctx, android.R.layout.simple_expandable_list_item_1,
				new ArrayList<AvailableForumsItem>());
		pad = LayoutUtils.getPadding(ctx);
	}

	@Override
	public View getView(int position, View convertView, ViewGroup parent) {
		AvailableForumsItem item = getItem(position);
		Context ctx = getContext();

		LinearLayout layout = new LinearLayout(ctx);
		layout.setOrientation(VERTICAL);

		TextView name = new TextView(ctx);
		name.setTextSize(18);
		name.setSingleLine();
		name.setEllipsize(END);
		name.setPadding(pad, pad, pad, pad);
		name.setText(item.getForum().getName());
		layout.addView(name);

		TextView status = new TextView(ctx);
		status.setPadding(pad, 0, pad, pad);
		Collection<String> names = new ArrayList<String>();
		for (Contact c : item.getContacts()) names.add(c.getAuthor().getName());
		String format = ctx.getString(R.string.shared_by_format);
		status.setText(String.format(format, StringUtils.join(names, ", ")));
		layout.addView(status);

		return layout;
	}
}
