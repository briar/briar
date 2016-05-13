package org.briarproject.android.forum;

import android.content.Context;
import android.support.v4.content.ContextCompat;
import android.text.format.DateUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import org.briarproject.R;
import org.briarproject.android.contact.BaseContactListAdapter;
import org.briarproject.android.contact.ContactListItem;

public class ForumSharingStatusAdapter
		extends BaseContactListAdapter<BaseContactListAdapter.BaseContactHolder> {

	public ForumSharingStatusAdapter(Context context) {
		super(context, null);
	}

	@Override
	public BaseContactHolder onCreateViewHolder(ViewGroup viewGroup, int i) {
		View v = LayoutInflater.from(viewGroup.getContext()).inflate(
				R.layout.list_item_contact_small, viewGroup, false);

		return new BaseContactHolder(v);
	}

	@Override
	public int compareContactListItems(ContactListItem c1, ContactListItem c2) {
		return compareByName(c1, c2);
	}

}
