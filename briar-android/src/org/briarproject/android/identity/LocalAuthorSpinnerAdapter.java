package org.briarproject.android.identity;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.SpinnerAdapter;
import android.widget.TextView;

import org.briarproject.R;
import org.briarproject.api.crypto.CryptoComponent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import im.delight.android.identicons.IdenticonDrawable;

import static org.briarproject.android.identity.LocalAuthorItem.ANONYMOUS;
import static org.briarproject.android.identity.LocalAuthorItem.NEW;

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
		notifyDataSetChanged();
	}

	public void clear() {
		list.clear();
		notifyDataSetChanged();
	}

	public int getCount() {
		if (list.isEmpty()) return 0;
		return includeAnonymous ? list.size() + 2 : list.size() + 1;
	}

	@Override
	public View getDropDownView(int position, View convertView,
			ViewGroup parent) {
		View view;
		if (convertView == null) {
			LayoutInflater inflater =
					(LayoutInflater) ctx
							.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
			view = inflater.inflate(R.layout.dropdown_author, parent, false);
		} else
			view = convertView;

		TextView name = (TextView) view.findViewById(R.id.nameView);
		ImageView avatar =
				(ImageView) view.findViewById(R.id.avatarView);

		LocalAuthorItem item = getItem(position);
		if (item == ANONYMOUS) {
			name.setText(R.string.anonymous);
			avatar.setVisibility(View.INVISIBLE);
		} else if (item == NEW) {
			name.setText(R.string.new_identity_item);
			avatar.setVisibility(View.INVISIBLE);
		} else {
			name.setText(item.getLocalAuthor().getName());
			avatar.setVisibility(View.VISIBLE);
			avatar.setImageDrawable(new IdenticonDrawable(
					item.getLocalAuthor().getId().getBytes()));
		}
		return view;
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
		return getDropDownView(position, convertView, parent);
	}

	@Override
	public boolean isEmpty() {
		return list.isEmpty();
	}

	public void sort(Comparator<LocalAuthorItem> comparator) {
		Collections.sort(list, comparator);
		notifyDataSetChanged();
	}
}
