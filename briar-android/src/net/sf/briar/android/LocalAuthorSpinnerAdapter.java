package net.sf.briar.android;

import java.util.ArrayList;

import net.sf.briar.R;
import net.sf.briar.api.LocalAuthor;
import android.content.Context;
import android.content.res.Resources;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.SpinnerAdapter;
import android.widget.TextView;

public class LocalAuthorSpinnerAdapter extends ArrayAdapter<LocalAuthor>
implements SpinnerAdapter {

	public LocalAuthorSpinnerAdapter(Context ctx) {
		super(ctx, android.R.layout.simple_spinner_item,
				new ArrayList<LocalAuthor>());
	}

	@Override
	public View getView(int position, View convertView, ViewGroup parent) {
		TextView name = new TextView(getContext());
		name.setTextSize(18);
		name.setMaxLines(1);
		Resources res = getContext().getResources();
		int pad = res.getInteger(R.integer.spinner_padding);
		name.setPadding(pad, pad, pad, pad);
		name.setText(getItem(position).getName());
		return name;
	}

	@Override
	public View getDropDownView(int position, View convertView,
			ViewGroup parent) {
		return getView(position, convertView, parent);
	}
}
