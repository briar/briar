package org.briarproject.briar.android.contact;

import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.briar.R;

import javax.annotation.Nullable;

import androidx.annotation.UiThread;
import androidx.recyclerview.widget.RecyclerView;

import static org.briarproject.briar.android.util.UiUtils.getContactDisplayName;
import static org.briarproject.briar.android.view.AuthorView.setAvatar;

@UiThread
@NotNullByDefault
public class ContactItemViewHolder<I extends ContactItem>
		extends RecyclerView.ViewHolder {

	protected final ViewGroup layout;
	protected final ImageView avatar;
	protected final TextView name;
	@Nullable
	protected final ImageView bulb;

	public ContactItemViewHolder(View v) {
		super(v);

		layout = (ViewGroup) v;
		avatar = v.findViewById(R.id.avatarView);
		name = v.findViewById(R.id.nameView);
		// this can be null as not all layouts that use this ViewHolder have it
		bulb = v.findViewById(R.id.bulbView);
	}

	protected void bind(I item, @Nullable OnContactClickListener<I> listener) {
		setAvatar(avatar, item);
		name.setText(getContactDisplayName(item.getContact()));

		if (bulb != null) {
			// online/offline
			if (item.isConnected()) {
				bulb.setImageResource(R.drawable.contact_connected);
			} else {
				bulb.setImageResource(R.drawable.contact_disconnected);
			}
		}

		layout.setOnClickListener(v -> {
			if (listener != null) listener.onItemClick(avatar, item);
		});
	}

}
