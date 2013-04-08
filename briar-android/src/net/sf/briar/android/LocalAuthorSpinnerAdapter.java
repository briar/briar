package net.sf.briar.android;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import net.sf.briar.R;
import net.sf.briar.api.Author;
import net.sf.briar.api.LocalAuthor;
import android.content.Context;
import android.content.res.Resources;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.SpinnerAdapter;
import android.widget.TextView;

public class LocalAuthorSpinnerAdapter extends BaseAdapter
implements SpinnerAdapter {

	private final Context ctx;
	private final List<LocalAuthor> list = new ArrayList<LocalAuthor>();

	public LocalAuthorSpinnerAdapter(Context ctx) {
		this.ctx = ctx;
	}

	public void add(LocalAuthor a) {
		list.add(a);
		notifyDataSetChanged();
	}

	public void clear() {
		list.clear();
		notifyDataSetChanged();
	}

	public int getCount() {
		return list.size() + 1;
	}

	public LocalAuthor getItem(int position) {
		if(position == list.size()) return null;
		return list.get(position);
	}

	public long getItemId(int position) {
		return android.R.layout.simple_spinner_item;
	}

	public void sort(Comparator<Author> comparator) {
		Collections.sort(list, comparator);
	}

	public View getView(int position, View convertView, ViewGroup parent) {
		Log.i("Briar", "getView: " + position);
		TextView name = new TextView(ctx);
		name.setTextSize(18);
		name.setMaxLines(1);
		Resources res = ctx.getResources();
		int pad = res.getInteger(R.integer.spinner_padding);
		name.setPadding(pad, pad, pad, pad);
		if(position == list.size()) name.setText(R.string.create_identity_item);
		else name.setText(list.get(position).getName());
		return name;
	}

	@Override
	public View getDropDownView(int position, View convertView,
			ViewGroup parent) {
		Log.i("Briar", "getDropDownView: " + position);
		return getView(position, convertView, parent);
	}
}
