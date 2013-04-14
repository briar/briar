package net.sf.briar.android.contact;

import static net.sf.briar.android.contact.ContactItem.NEW;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import net.sf.briar.R;
import android.content.Context;
import android.content.res.Resources;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.SpinnerAdapter;
import android.widget.TextView;

public class ContactSpinnerAdapter extends BaseAdapter
implements SpinnerAdapter {

	private final Context ctx;
	private final List<ContactItem> list = new ArrayList<ContactItem>();

	public ContactSpinnerAdapter(Context ctx) {
		this.ctx = ctx;
	}

	public void add(ContactItem item) {
		list.add(item);
	}

	public void clear() {
		list.clear();
	}

	public int getCount() {
		return list.isEmpty() ? 0 : list.size() + 1;
	}

	@Override
	public View getDropDownView(int position, View convertView,
			ViewGroup parent) {
		return getView(position, convertView, parent);
	}

	public ContactItem getItem(int position) {
		if(position == list.size()) return NEW;
		return list.get(position);
	}

	public long getItemId(int position) {
		return android.R.layout.simple_spinner_item;
	}

	public View getView(int position, View convertView, ViewGroup parent) {
		TextView name = new TextView(ctx);
		name.setTextSize(18);
		name.setMaxLines(1);
		Resources res = ctx.getResources();
		int pad = res.getInteger(R.integer.spinner_padding);
		name.setPadding(pad, pad, pad, pad);
		ContactItem item = getItem(position);
		if(item == NEW) name.setText(R.string.new_contact_item);
		else name.setText(item.getContact().getAuthor().getName());
		return name;
	}

	@Override
	public boolean isEmpty() {
		return list.isEmpty();
	}

	public void sort(Comparator<ContactItem> comparator) {
		Collections.sort(list, comparator);
	}
}
