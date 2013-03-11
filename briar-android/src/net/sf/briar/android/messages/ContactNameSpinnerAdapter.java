package net.sf.briar.android.messages;

import java.util.ArrayList;

import net.sf.briar.api.Contact;
import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.SpinnerAdapter;
import android.widget.TextView;

class ContactNameSpinnerAdapter extends ArrayAdapter<Contact>
implements SpinnerAdapter {

	ContactNameSpinnerAdapter(Context context) {
		super(context, android.R.layout.simple_spinner_item,
				new ArrayList<Contact>());
	}

	@Override
	public View getView(int position, View convertView, ViewGroup parent) {
		TextView name = new TextView(getContext());
		name.setTextSize(18);
		name.setPadding(10, 10, 10, 10);
		name.setText(getItem(position).getName());
		return name;
	}

	@Override
	public View getDropDownView(int position, View convertView,
			ViewGroup parent) {
		return getView(position, convertView, parent);
	}
}
