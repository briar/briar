package org.briarproject.android.identity;

import static android.text.TextUtils.TruncateAt.END;
import static org.briarproject.android.identity.LocalAuthorItem.ANONYMOUS;
import static org.briarproject.android.identity.LocalAuthorItem.NEW;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import org.briarproject.R;
import org.briarproject.android.util.LayoutUtils;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.SpinnerAdapter;
import android.widget.TextView;

public class LocalAuthorSpinnerAdapter extends BaseAdapter
implements SpinnerAdapter {

	private final Context ctx;
	private final boolean includeAnonymous;
	private final List<LocalAuthorItem> list = new ArrayList<LocalAuthorItem>();

	public LocalAuthorSpinnerAdapter(Context ctx, boolean includeAnonymous) {
		this.ctx = ctx;
		this.includeAnonymous = includeAnonymous;
	}

	public void add(LocalAuthorItem item) {
		list.add(item);
	}

	public void clear() {
		list.clear();
	}

	public int getCount() {
		if (list.isEmpty()) return 0;
		return includeAnonymous ? list.size() + 2 : list.size() + 1;
	}

	@Override
	public View getDropDownView(int position, View convertView,
			ViewGroup parent) {
		TextView name = new TextView(ctx);
		name.setTextSize(18);
		name.setSingleLine();
		name.setEllipsize(END);
		int pad = LayoutUtils.getPadding(ctx);
		name.setPadding(pad, pad, pad, pad);
		LocalAuthorItem item = getItem(position);
		if (item == ANONYMOUS) name.setText(R.string.anonymous);
		else if (item == NEW) name.setText(R.string.new_identity_item);
		else name.setText(item.getLocalAuthor().getName());
		return name;
	}

	public LocalAuthorItem getItem(int position) {
		if (includeAnonymous) {
			if (position == list.size()) return ANONYMOUS;
			if (position == list.size() + 1) return NEW;
			return list.get(position);
		} else {
			if (position == list.size()) return NEW;
			return list.get(position);
		}
	}

	public long getItemId(int position) {
		return android.R.layout.simple_spinner_item;
	}

	public View getView(int position, View convertView, ViewGroup parent) {
		TextView name = new TextView(ctx);
		name.setTextSize(18);
		name.setSingleLine();
		name.setEllipsize(END);
		LocalAuthorItem item = getItem(position);
		if (item == ANONYMOUS) name.setText(R.string.anonymous);
		else if (item == NEW) name.setText(R.string.new_identity_item);
		else name.setText(item.getLocalAuthor().getName());
		return name;
	}

	@Override
	public boolean isEmpty() {
		return list.isEmpty();
	}

	public void sort(Comparator<LocalAuthorItem> comparator) {
		Collections.sort(list, comparator);
	}
}
