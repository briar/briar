package org.briarproject.android.contact;

import android.content.Context;
import android.support.v7.util.SortedList;
import android.support.v7.widget.RecyclerView;
import android.text.format.DateUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import org.briarproject.R;
import org.briarproject.api.crypto.CryptoComponent;
import org.briarproject.api.messaging.PrivateMessageHeader;
import org.briarproject.util.StringUtils;

import im.delight.android.identicons.IdenticonDrawable;

import static android.support.v7.util.SortedList.INVALID_POSITION;

class ConversationAdapter extends
		RecyclerView.Adapter<ConversationAdapter.MessageHolder> {

	private static final int MSG_OUT = 0;
	private static final int MSG_IN = 1;
	private static final int MSG_IN_UNREAD = 2;

	private final SortedList<ConversationItem> messages =
			new SortedList<ConversationItem>(ConversationItem.class,
					new SortedList.Callback<ConversationItem>() {
						@Override
						public void onInserted(int position, int count) {
							notifyItemRangeInserted(position, count);
						}

						@Override
						public void onChanged(int position, int count) {
							notifyItemRangeChanged(position, count);
						}

						@Override
						public void onMoved(int fromPosition, int toPosition) {
							notifyItemMoved(fromPosition, toPosition);
						}

						@Override
						public void onRemoved(int position, int count) {
							notifyItemRangeRemoved(position, count);
						}

						@Override
						public int compare(ConversationItem c1,
								ConversationItem c2) {
							long time1 = c1.getHeader().getTimestamp();
							long time2 = c2.getHeader().getTimestamp();
							if (time1 < time2) return -1;
							if (time1 > time2) return 1;
							return 0;
						}

						@Override
						public boolean areItemsTheSame(ConversationItem c1,
								ConversationItem c2) {
							return c1.getHeader().getId()
									.equals(c2.getHeader().getId());
						}

						@Override
						public boolean areContentsTheSame(ConversationItem c1,
								ConversationItem c2) {
							return c1.equals(c2);
						}
					});
	private Context ctx;
	private CryptoComponent crypto;
	private byte[] identiconKey;

	public ConversationAdapter(Context context, CryptoComponent cryptoComponent) {
		ctx = context;
		crypto = cryptoComponent;
	}

	public void setIdenticonKey(byte[] key) {
		this.identiconKey = key;
		notifyDataSetChanged();
	}

	@Override
	public int getItemViewType(int position) {
		// return different type for incoming and outgoing (local) messages
		PrivateMessageHeader header = getItem(position).getHeader();
		if (header.isLocal()) {
			return MSG_OUT;
		} else if (header.isRead()) {
			return MSG_IN;
		} else {
			return MSG_IN_UNREAD;
		}
	}

	@Override
	public MessageHolder onCreateViewHolder(ViewGroup viewGroup, int type) {
		View v;

		// outgoing message (local)
		if (type == MSG_OUT) {
			v = LayoutInflater.from(viewGroup.getContext())
					.inflate(R.layout.list_item_msg_out, viewGroup, false);
		}
		// incoming message (non-local)
		else {
			v = LayoutInflater.from(viewGroup.getContext())
					.inflate(R.layout.list_item_msg_in, viewGroup, false);
		}

		return new MessageHolder(v, type);
	}

	@Override
	public void onBindViewHolder(final MessageHolder ui, final int position) {
		ConversationItem item = getItem(position);
		PrivateMessageHeader header = item.getHeader();

		if (header.isLocal()) {
			if (item.isSeen()) {
				ui.status.setImageResource(R.drawable.message_delivered);
			} else if (item.isSent()) {
				ui.status.setImageResource(R.drawable.message_sent);
			} else {
				ui.status.setImageResource(R.drawable.message_stored);
			}
		} else {
			if (identiconKey != null)
				ui.avatar.setImageDrawable(
						new IdenticonDrawable(crypto, identiconKey));
			if (!header.isRead()) {
				int left = ui.layout.getPaddingLeft();
				int top = ui.layout.getPaddingTop();
				int right = ui.layout.getPaddingRight();
				int bottom = ui.layout.getPaddingBottom();

				// show unread messages in different color to not miss them
				ui.layout.setBackgroundResource(R.drawable.msg_in_unread);

				// re-apply the previous padding due to bug in some Android versions
				// see: https://code.google.com/p/android/issues/detail?id=17885
				ui.layout.setPadding(left, top, right, bottom);
			}
		}

		if (item.getBody() == null) {
			ui.body.setText("\u2026");
		} else if (header.getContentType().equals("text/plain")) {
			ui.body.setText(StringUtils.fromUtf8(item.getBody()));
		} else {
			// TODO support other content types
		}

		long timestamp = header.getTimestamp();
		ui.date.setText(DateUtils.getRelativeTimeSpanString(ctx, timestamp));
	}

	@Override
	public int getItemCount() {
		return messages.size();
	}

	public ConversationItem getItem(int position) {
		if (position == INVALID_POSITION || messages.size() <= position) {
			return null; // Not found
		}
		return messages.get(position);
	}

	public ConversationItem getLastItem() {
		if (messages.size() > 0) {
			return messages.get(messages.size() - 1);
		} else {
			return null;
		}
	}

	public void add(final ConversationItem message) {
		this.messages.add(message);
	}

	public void clear() {
		this.messages.beginBatchedUpdates();

		while(messages.size() != 0) {
			messages.removeItemAt(0);
		}

		this.messages.endBatchedUpdates();
	}

	// TODO: Does this class need to be public?
	public static class MessageHolder extends RecyclerView.ViewHolder {

		public ViewGroup layout;
		public TextView body;
		public TextView date;
		public ImageView status;
		public ImageView avatar;

		public MessageHolder(View v, int type) {
			super(v);

			layout = (ViewGroup) v.findViewById(R.id.msgLayout);
			body = (TextView) v.findViewById(R.id.msgBody);
			date = (TextView) v.findViewById(R.id.msgTime);

			// outgoing message (local)
			if (type == MSG_OUT) {
				status = (ImageView) v.findViewById(R.id.msgStatus);
			} else {
				avatar = (ImageView) v.findViewById(R.id.msgAvatar);
			}
		}
	}
}