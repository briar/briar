package org.briarproject.android.contact;

import android.support.annotation.UiThread;
import android.support.v7.widget.RecyclerView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import org.briarproject.R;
import org.briarproject.android.contact.BaseContactListAdapter.OnContactClickListener;
import org.briarproject.api.identity.Author;
import org.briarproject.api.nullsafety.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import im.delight.android.identicons.IdenticonDrawable;

@UiThread
@NotNullByDefault
public class ContactItemViewHolder<I extends ContactItem>
		extends RecyclerView.ViewHolder {

	protected final ViewGroup layout;
	protected final ImageView avatar;
	protected final TextView name;

	public ContactItemViewHolder(View v) {
		super(v);

		layout = (ViewGroup) v;
		avatar = (ImageView) v.findViewById(R.id.avatarView);
		name = (TextView) v.findViewById(R.id.nameView);
	}

	protected void bind(final I item,
			@Nullable final OnContactClickListener<I> listener) {
		Author author = item.getContact().getAuthor();
		avatar.setImageDrawable(
				new IdenticonDrawable(author.getId().getBytes()));
		String contactName = author.getName();
		name.setText(contactName);

		layout.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				if (listener != null) listener.onItemClick(avatar, item);
			}
		});
	}

}
