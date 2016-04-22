package org.briarproject.android.introduction;

import android.content.Context;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.os.Build;
import android.view.View;

import org.briarproject.android.contact.ContactListAdapter;
import org.briarproject.android.contact.ContactListItem;
import org.briarproject.api.identity.AuthorId;

public class ContactChooserAdapter extends ContactListAdapter {

	private AuthorId localAuthorId;

	public ContactChooserAdapter(Context context,
			OnItemClickListener listener) {

		super(context, listener);
	}

	@Override
	public void onBindViewHolder(final ContactHolder ui, final int position) {
		super.onBindViewHolder(ui, position);

		final ContactListItem item = getItem(position);

		ui.name.setText(item.getContact().getAuthor().getName());

		ui.identity.setText(item.getLocalAuthor().getName());
		ui.identity.setVisibility(View.VISIBLE);

		if (!item.getLocalAuthor().getId().equals(localAuthorId)) {
			grayOutItem(ui);
		}
	}

	@Override
	public int compareContactListItems(ContactListItem c1, ContactListItem c2) {
		return compareByName(c1, c2);
	}

	/**
	 * Set the identity from whose perspective the contact shall be chosen.
	 * Contacts that belong to a different author will be shown grayed out,
	 * but are still clickable.
	 * @param authorId The ID of the local Author
	 */
	public void setLocalAuthor(AuthorId authorId) {
		localAuthorId = authorId;
		notifyDataSetChanged();
	}

	private void grayOutItem(final ContactHolder ui) {
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
			float alpha = 0.25f;
			ui.bulb.setAlpha(alpha);
			ui.avatar.setAlpha(alpha);
			ui.name.setAlpha(alpha);
			ui.date.setAlpha(alpha);
			ui.identity.setAlpha(alpha);
		} else {
			ColorFilter colorFilter = new PorterDuffColorFilter(Color.GRAY,
					PorterDuff.Mode.MULTIPLY);
			ui.bulb.setColorFilter(colorFilter);
			ui.avatar.setColorFilter(colorFilter);
			ui.name.setEnabled(false);
			ui.date.setEnabled(false);
		}
	}

}
