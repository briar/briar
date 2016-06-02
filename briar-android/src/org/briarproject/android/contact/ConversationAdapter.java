package org.briarproject.android.contact;

import android.content.Context;
import android.content.Intent;
import android.support.v7.util.SortedList;
import android.support.v7.widget.RecyclerView;
import android.util.SparseArray;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import org.briarproject.R;
import org.briarproject.android.forum.ForumInvitationsActivity;
import org.briarproject.android.util.AndroidUtils;
import org.briarproject.api.UniqueId;
import org.briarproject.api.conversation.ConversationForumInvitationItem;
import org.briarproject.api.conversation.ConversationIntroductionRequestItem;
import org.briarproject.api.conversation.ConversationIntroductionResponseItem;
import org.briarproject.api.conversation.ConversationItem;
import org.briarproject.api.conversation.ConversationItem.IncomingItem;
import org.briarproject.api.conversation.ConversationItem.OutgoingItem;
import org.briarproject.api.conversation.ConversationMessageItem;
import org.briarproject.api.forum.ForumInvitationMessage;
import org.briarproject.api.introduction.IntroductionRequest;
import org.briarproject.api.introduction.IntroductionResponse;
import org.briarproject.api.messaging.PrivateMessageHeader;
import org.briarproject.util.StringUtils;

import java.util.Arrays;
import java.util.List;

import static android.support.v7.util.SortedList.INVALID_POSITION;
import static android.support.v7.widget.RecyclerView.NO_ID;
import static android.support.v7.widget.RecyclerView.ViewHolder;

class ConversationAdapter extends RecyclerView.Adapter {

	private final SortedList<ConversationItem> items =
			new SortedList<>(ConversationItem.class, new ListCallbacks());

	private Context ctx;
	private ConversationHandler handler;
	private MessageUpdatedHandler msgUpdated;
	private String contactName;

	public ConversationAdapter(Context context,
			ConversationHandler conversationHandler,
			MessageUpdatedHandler messageUpdatedHandler) {
		ctx = context;
		handler = conversationHandler;
		msgUpdated = messageUpdatedHandler;
		setHasStableIds(true);
	}

	public void setContactName(String contactName) {
		this.contactName = contactName;
		notifyDataSetChanged();
	}

	@Override
	public int getItemViewType(int position) {
		ConversationItem m = getItem(position);
		if (m instanceof IncomingItem) {
			if (m instanceof ConversationMessageItem) {
				return R.layout.list_item_msg_in;
			} else if (m instanceof ConversationIntroductionRequestItem) {
				return R.layout.list_item_introduction_in;
			} else if (m instanceof ConversationIntroductionResponseItem) {
				return R.layout.list_item_notice_in;
			} else if (m instanceof ConversationForumInvitationItem) {
				return R.layout.list_item_forum_invitation_in;
			}
		} else if (m instanceof OutgoingItem) {
			if (m instanceof ConversationMessageItem) {
				return R.layout.list_item_msg_out;
			} else if (m instanceof ConversationIntroductionRequestItem) {
				return R.layout.list_item_introduction_out;
			} else if (m instanceof ConversationIntroductionResponseItem) {
				return R.layout.list_item_notice_out;
			} else if (m instanceof ConversationForumInvitationItem) {
				return R.layout.list_item_forum_invitation_out;
			}
		}
		throw new IllegalArgumentException("Unhandled Conversation Message");
	}

	@Override
	public ViewHolder onCreateViewHolder(ViewGroup viewGroup, int type) {
		View v = LayoutInflater.from(viewGroup.getContext()).inflate(
				type, viewGroup, false);

		switch (type) {
			case R.layout.list_item_msg_in:
				return new MessageHolder(v, false);
			case R.layout.list_item_msg_out:
				return new MessageHolder(v, true);

			case R.layout.list_item_introduction_in:
				return new IntroductionHolder(v, false);
			case R.layout.list_item_introduction_out:
				return new IntroductionHolder(v, true);

			case R.layout.list_item_notice_in:
				return new NoticeHolder(v, false);
			case R.layout.list_item_notice_out:
				return new NoticeHolder(v, true);

			case R.layout.list_item_forum_invitation_in:
				return new InvitationHolder(v, false);
			case R.layout.list_item_forum_invitation_out:
				return new InvitationHolder(v, true);

			default:
				throw new IllegalArgumentException(
						"Unhandled Conversation Message");
		}
	}

	@Override
	public void onBindViewHolder(ViewHolder ui, int position) {
		switch (ui.getItemViewType()) {
			case R.layout.list_item_msg_in:
			case R.layout.list_item_msg_out:
				bindMessage((MessageHolder) ui, position);
				break;

			case R.layout.list_item_introduction_in:
			case R.layout.list_item_introduction_out:
				bindIntroduction((IntroductionHolder) ui, position);
				break;

			case R.layout.list_item_notice_in:
			case R.layout.list_item_notice_out:
				bindNotice((NoticeHolder) ui, position);
				break;

			case R.layout.list_item_forum_invitation_in:
			case R.layout.list_item_forum_invitation_out:
				bindInvitation((InvitationHolder) ui, position);
				break;

			default:
				throw new IllegalArgumentException(
						"Unhandled Conversation Message");
		}
	}

	private void bindMessage(final MessageHolder ui, int position) {

		ConversationMessageItem item =
				(ConversationMessageItem) getItem(position);
		PrivateMessageHeader header = item.getHeader();

		if (item instanceof OutgoingItem) {
			if (((OutgoingItem) item).isSeen()) {
				ui.status.setImageResource(R.drawable.message_delivered_white);
			} else if (((OutgoingItem) item).isSent()) {
				ui.status.setImageResource(R.drawable.message_sent_white);
			} else {
				ui.status.setImageResource(R.drawable.message_stored_white);
			}
		} else {
			if (!((IncomingItem) item).isRead()) {
				// TODO implement new unread message highlight according to #232
/*				int left = ui.layout.getPaddingLeft();
				int top = ui.layout.getPaddingTop();
				int right = ui.layout.getPaddingRight();
				int bottom = ui.layout.getPaddingBottom();

				// show unread messages in different color to not miss them
				ui.layout.setBackgroundResource(R.drawable.msg_in_unread);

				// re-apply the previous padding due to bug in some Android versions
				// see: https://code.google.com/p/android/issues/detail?id=17885
				ui.layout.setPadding(left, top, right, bottom);
*/
			}
		}

		if (item.getBody() == null) {
			ui.body.setText("\u2026");
			item.setContentListener(new ConversationItem.ContentListener() {
				@Override
				public void contentReady() {
					msgUpdated.messageUpdated(ui.getAdapterPosition());
				}
			});
		} else if (header.getContentType().equals("text/plain")) {
			ui.body.setText(
					StringUtils.trim(StringUtils.fromUtf8(item.getBody())));
		} else {
			// TODO support other content types
		}

		long timestamp = header.getTimestamp();
		ui.date.setText(AndroidUtils.formatDate(ctx, timestamp));
	}

	private void bindIntroduction(IntroductionHolder ui, final int position) {

		final ConversationIntroductionRequestItem item =
				(ConversationIntroductionRequestItem) getItem(position);
		final IntroductionRequest ir = item.getIntroductionRequest();

		String message = ir.getMessage();
		if (StringUtils.isNullOrEmpty(message)) {
			ui.messageLayout.setVisibility(View.GONE);
		} else {
			ui.messageLayout.setVisibility(View.VISIBLE);
			ui.message.body.setText(StringUtils.trim(message));
			ui.message.date
					.setText(AndroidUtils.formatDate(ctx, item.getTime()));
		}

		// Outgoing Introduction Request
		if (item instanceof OutgoingItem) {
			ui.text.setText(ctx.getString(R.string.introduction_request_sent,
					contactName, ir.getName()));
			OutgoingItem i = (OutgoingItem) item;
			if (i.isSeen()) {
				ui.status.setImageResource(R.drawable.message_delivered);
				ui.message.status.setImageResource(
						R.drawable.message_delivered_white);
			} else if (i.isSent()) {
				ui.status.setImageResource(R.drawable.message_sent);
				ui.message.status.setImageResource(
						R.drawable.message_sent_white);
			} else {
				ui.status.setImageResource(R.drawable.message_stored);
				ui.message.status.setImageResource(
						R.drawable.message_stored_white);
			}
		}
		// Incoming Introduction Request (Answered)
		else if (item.wasAnswered()) {
			ui.text.setText(ctx.getString(
					R.string.introduction_request_answered_received,
					contactName, ir.getName()));
			ui.acceptButton.setVisibility(View.GONE);
			ui.declineButton.setVisibility(View.GONE);
		}
		// Incoming Introduction Request (Not Answered)
		else {
			if (item.getIntroductionRequest().contactExists()) {
				ui.text.setText(ctx.getString(
						R.string.introduction_request_exists_received,
						contactName, ir.getName()));
			} else {
				ui.text.setText(
						ctx.getString(R.string.introduction_request_received,
								contactName, ir.getName()));
			}

			if (item.getIntroductionRequest().doesIntroduceOtherIdentity()) {
				// don't allow accept when one of our identities is introduced
				ui.acceptButton.setVisibility(View.GONE);
				ui.text.setText(ctx.getString(
						R.string.introduction_request_for_our_identity_received,
						contactName, ir.getName()));
			} else {
				ui.acceptButton.setVisibility(View.VISIBLE);
				ui.acceptButton.setOnClickListener(new View.OnClickListener() {
					@Override
					public void onClick(View v) {
						handler.respondToItem(item, true);
						item.setAnswered(true);
						notifyItemChanged(position);
					}
				});
			}
			ui.declineButton.setVisibility(View.VISIBLE);
			ui.declineButton.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					handler.respondToItem(item, false);
					item.setAnswered(true);
					notifyItemChanged(position);
				}
			});
		}
		ui.date.setText(AndroidUtils.formatDate(ctx, item.getTime()));
	}

	private void bindNotice(NoticeHolder ui, int position) {
		ConversationItem item = getItem(position);

		ui.text.setText(getNoticeText(item));
		ui.date.setText(AndroidUtils.formatDate(ctx, item.getTime()));

		if (item instanceof OutgoingItem) {
			OutgoingItem n = (OutgoingItem) item;
			if (n.isSeen()) {
				ui.status.setImageResource(R.drawable.message_delivered);
			} else if (n.isSent()) {
				ui.status.setImageResource(R.drawable.message_sent);
			} else {
				ui.status.setImageResource(R.drawable.message_stored);
			}
		}
	}

	private String getNoticeText(ConversationItem m) {
		if (m instanceof ConversationIntroductionResponseItem) {
			IntroductionResponse ir =
					((ConversationIntroductionResponseItem) m)
							.getIntroductionResponse();

			if (ir.isLocal()) {
				if (ir.wasAccepted()) {
					return ctx.getString(
							R.string.introduction_response_accepted_sent,
							ir.getName());
				} else {
					return ctx.getString(
							R.string.introduction_response_declined_sent,
							ir.getName());
				}
			} else {
				if (ir.wasAccepted()) {
					return ctx.getString(
							R.string.introduction_response_accepted_received,
							contactName, ir.getName());
				} else {
					if (ir.isIntroducer()) {
						return ctx.getString(
								R.string.introduction_response_declined_received,
								contactName, ir.getName());
					} else {
						return ctx.getString(
								R.string.introduction_response_declined_received_by_introducee,
								contactName, ir.getName());
					}
				}
			}
		} else {
			throw new IllegalArgumentException(
					"Unhandled Conversation Message");
		}
	}

	private void bindInvitation(InvitationHolder ui, int position) {

		ConversationForumInvitationItem item =
				(ConversationForumInvitationItem) getItem(position);
		ForumInvitationMessage fim = item.getForumInvitationMessage();

		String message = fim.getMessage();
		if (StringUtils.isNullOrEmpty(message)) {
			ui.messageLayout.setVisibility(View.GONE);
		} else {
			ui.messageLayout.setVisibility(View.VISIBLE);
			ui.message.body.setText(StringUtils.trim(message));
			ui.message.date
					.setText(AndroidUtils.formatDate(ctx, item.getTime()));
		}

		// Outgoing Invitation
		if (item instanceof OutgoingItem) {
			ui.text.setText(ctx.getString(R.string.forum_invitation_sent,
					fim.getForumName(), contactName));
			OutgoingItem i = (OutgoingItem) item;
			if (i.isSeen()) {
				ui.status.setImageResource(R.drawable.message_delivered);
				ui.message.status.setImageResource(
						R.drawable.message_delivered_white);
			} else if (i.isSent()) {
				ui.status.setImageResource(R.drawable.message_sent);
				ui.message.status.setImageResource(
						R.drawable.message_sent_white);
			} else {
				ui.status.setImageResource(R.drawable.message_stored);
				ui.message.status.setImageResource(
						R.drawable.message_stored_white);
			}
		}
		// Incoming Invitation
		else {
			ui.text.setText(ctx.getString(R.string.forum_invitation_received,
					contactName, fim.getForumName()));

			if (fim.isAvailable()) {
				ui.showForumsButton.setVisibility(View.VISIBLE);
				ui.showForumsButton
						.setOnClickListener(new View.OnClickListener() {
							@Override
							public void onClick(View v) {
								Intent intent = new Intent(ctx,
										ForumInvitationsActivity.class);
								ctx.startActivity(intent);
							}
						});
			} else {
				ui.showForumsButton.setVisibility(View.GONE);
			}
		}
		ui.date.setText(AndroidUtils.formatDate(ctx, item.getTime()));
	}

	@Override
	public int getItemCount() {
		return items.size();
	}

	@Override
	public long getItemId(int position) {
		ConversationItem m = getItem(position);
		if (m == null) {
			return NO_ID;
		}
		byte[] b = m.getId().getBytes();
		// Technically this could result in collisions because hashCode is not
		// guaranteed to be collision-resistant. We could instead use BLAKE2s
		// with hash output set to 8 bytes, and then convert that to a long.
		long id = Arrays.hashCode(Arrays.copyOf(b, UniqueId.LENGTH / 2));
		id <<= 32;
		id |= Arrays.hashCode(
				Arrays.copyOfRange(b, UniqueId.LENGTH / 2, UniqueId.LENGTH));
		return id;
	}

	public ConversationItem getItem(int position) {
		if (position == INVALID_POSITION || items.size() <= position) {
			return null; // Not found
		}
		return items.get(position);
	}

	public ConversationItem getLastItem() {
		if (items.size() > 0) {
			return items.get(items.size() - 1);
		} else {
			return null;
		}
	}

	public SparseArray<IncomingItem> getIncomingMessages() {
		SparseArray<IncomingItem> messages =
				new SparseArray<>();

		for (int i = 0; i < items.size(); i++) {
			ConversationItem item = items.get(i);
			if (item instanceof IncomingItem) {
				messages.put(i, (IncomingItem) item);
			}
		}
		return messages;
	}

	public SparseArray<OutgoingItem> getOutgoingMessages() {
		SparseArray<OutgoingItem> messages =
				new SparseArray<>();

		for (int i = 0; i < items.size(); i++) {
			ConversationItem item = items.get(i);
			if (item instanceof OutgoingItem) {
				messages.put(i, (OutgoingItem) item);
			}
		}
		return messages;
	}

	public void add(final ConversationItem message) {
		this.items.add(message);
	}

	public void clear() {
		items.clear();
	}

	public void addAll(List<ConversationItem> items) {
		this.items.addAll(items);
	}

	private static class MessageHolder extends RecyclerView.ViewHolder {

		public ViewGroup layout;
		public TextView body;
		public TextView date;
		public ImageView status;

		public MessageHolder(View v, boolean outgoing) {
			super(v);

			layout = (ViewGroup) v.findViewById(R.id.msgLayout);
			body = (TextView) v.findViewById(R.id.msgBody);
			date = (TextView) v.findViewById(R.id.msgTime);

			// outgoing message (local)
			if (outgoing) {
				status = (ImageView) v.findViewById(R.id.msgStatus);
			}
		}
	}

	private static class IntroductionHolder extends RecyclerView.ViewHolder {

		private final View messageLayout;
		private final MessageHolder message;
		private final TextView text;
		private final Button acceptButton;
		private final Button declineButton;
		private final TextView date;
		private final ImageView status;

		public IntroductionHolder(View v, boolean outgoing) {
			super(v);

			messageLayout = v.findViewById(R.id.messageLayout);
			message = new MessageHolder(messageLayout, outgoing);
			text = (TextView) v.findViewById(R.id.introductionText);
			acceptButton = (Button) v.findViewById(R.id.acceptButton);
			declineButton = (Button) v.findViewById(R.id.declineButton);
			date = (TextView) v.findViewById(R.id.introductionTime);

			if (outgoing) {
				status = (ImageView) v.findViewById(R.id.introductionStatus);
			} else {
				status = null;
			}
		}
	}

	private static class NoticeHolder extends RecyclerView.ViewHolder {

		private final TextView text;
		private final TextView date;
		private final ImageView status;

		public NoticeHolder(View v, boolean outgoing) {
			super(v);

			text = (TextView) v.findViewById(R.id.noticeText);
			date = (TextView) v.findViewById(R.id.noticeTime);

			if (outgoing) {
				status = (ImageView) v.findViewById(R.id.noticeStatus);
			} else {
				status = null;
			}
		}
	}

	private static class InvitationHolder extends RecyclerView.ViewHolder {

		private final View messageLayout;
		private final MessageHolder message;
		private final TextView text;
		private final Button showForumsButton;
		private final TextView date;
		private final ImageView status;

		public InvitationHolder(View v, boolean outgoing) {
			super(v);

			messageLayout = v.findViewById(R.id.messageLayout);
			message = new MessageHolder(messageLayout, outgoing);
			text = (TextView) v.findViewById(R.id.introductionText);
			showForumsButton = (Button) v.findViewById(R.id.showForumsButton);
			date = (TextView) v.findViewById(R.id.introductionTime);

			if (outgoing) {
				status = (ImageView) v.findViewById(R.id.introductionStatus);
			} else {
				status = null;
			}
		}
	}

	private class ListCallbacks
			extends SortedList.Callback<ConversationItem> {

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
			long time1 = c1.getTime();
			long time2 = c2.getTime();
			if (time1 < time2) return -1;
			if (time1 > time2) return 1;
			return 0;
		}

		@Override
		public boolean areItemsTheSame(ConversationItem c1,
				ConversationItem c2) {
			return c1.getId().equals(c2.getId());
		}

		@Override
		public boolean areContentsTheSame(ConversationItem c1,
				ConversationItem c2) {
			return c1.equals(c2);
		}
	}

	public interface ConversationHandler {
		void respondToItem(ConversationItem item, boolean accept);
	}

	public interface MessageUpdatedHandler {
		void messageUpdated(int position);
	}
}
