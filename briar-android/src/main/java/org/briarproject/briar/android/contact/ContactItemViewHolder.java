package org.briarproject.briar.android.contact;

import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import org.briarproject.bramble.api.identity.Author;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.plugin.ConnectionStatus;
import org.briarproject.briar.R;
import org.briarproject.briar.android.contact.BaseContactListAdapter.OnContactClickListener;

import javax.annotation.Nullable;

import androidx.annotation.UiThread;
import androidx.recyclerview.widget.RecyclerView;
import im.delight.android.identicons.IdenticonDrawable;

import static org.briarproject.bramble.api.plugin.ConnectionStatus.CONNECTED;
import static org.briarproject.bramble.api.plugin.ConnectionStatus.RECENTLY_CONNECTED;
import static org.briarproject.briar.android.util.UiUtils.getContactDisplayName;

@UiThread
@NotNullByDefault
public class ContactItemViewHolder<I extends ContactItem>
		extends RecyclerView.ViewHolder {

	protected final ViewGroup layout;
	protected final ImageView avatar;
	protected final TextView name;
	@Nullable
	private final ImageView bulb;

	public ContactItemViewHolder(View v) {
		super(v);

		layout = (ViewGroup) v;
		avatar = v.findViewById(R.id.avatarView);
		name = v.findViewById(R.id.nameView);
		// this can be null as not all layouts that use this ViewHolder have it
		bulb = v.findViewById(R.id.bulbView);
	}

	protected void bind(I item, @Nullable OnContactClickListener<I> listener) {
		Author author = item.getContact().getAuthor();
		avatar.setImageDrawable(
				new IdenticonDrawable(author.getId().getBytes()));
		name.setText(getContactDisplayName(item.getContact()));

		if (bulb != null) {
			// online/offline
			ConnectionStatus status = item.getConnectionStatus();
			if (status == CONNECTED) {
				bulb.setImageResource(R.drawable.ic_connected);
			} else if (status == RECENTLY_CONNECTED) {
				bulb.setImageResource(R.drawable.ic_recently_connected);
			} else {
				bulb.setImageResource(R.drawable.ic_disconnected);
			}
		}

		layout.setOnClickListener(v -> {
			if (listener != null) listener.onItemClick(avatar, item);
		});
	}

}
