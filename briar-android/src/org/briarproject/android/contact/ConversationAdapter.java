package org.briarproject.android.contact;

import android.content.Context;
import android.content.Intent;
import android.support.annotation.Nullable;
import android.support.v7.widget.RecyclerView;
import android.util.SparseArray;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import org.briarproject.R;
import org.briarproject.android.sharing.InvitationsBlogActivity;
import org.briarproject.android.sharing.InvitationsForumActivity;
import org.briarproject.android.util.AndroidUtils;
import org.briarproject.android.util.BriarAdapter;
import org.briarproject.api.blogs.BlogInvitationRequest;
import org.briarproject.api.clients.SessionId;
import org.briarproject.api.forum.ForumInvitationRequest;
import org.briarproject.api.introduction.IntroductionRequest;
import org.briarproject.api.messaging.PrivateMessageHeader;
import org.briarproject.api.sharing.InvitationRequest;
import org.briarproject.util.StringUtils;

import static android.support.v7.widget.RecyclerView.ViewHolder;
import static android.view.View.GONE;
import static android.view.View.VISIBLE;
import static org.briarproject.android.contact.ConversationItem.BLOG_INVITATION_IN;
import static org.briarproject.android.contact.ConversationItem.BLOG_INVITATION_OUT;
import static org.briarproject.android.contact.ConversationItem.FORUM_INVITATION_IN;
import static org.briarproject.android.contact.ConversationItem.FORUM_INVITATION_OUT;
import static org.briarproject.android.contact.ConversationItem.INTRODUCTION_IN;
import static org.briarproject.android.contact.ConversationItem.INTRODUCTION_OUT;
import static org.briarproject.android.contact.ConversationItem.IncomingItem;
import static org.briarproject.android.contact.ConversationItem.MSG_IN_UNREAD;
import static org.briarproject.android.contact.ConversationItem.MSG_OUT;
import static org.briarproject.android.contact.ConversationItem.NOTICE_IN;
import static org.briarproject.android.contact.ConversationItem.NOTICE_OUT;
import static org.briarproject.android.contact.ConversationItem.OutgoingItem;

class ConversationAdapter extends BriarAdapter<ConversationItem, ViewHolder> {

	private IntroductionHandler intro;
	private String contactName;

	ConversationAdapter(Context ctx, IntroductionHandler introductionHandler) {
		super(ctx, ConversationItem.class);
		intro = introductionHandler;
	}

	void setContactName(String contactName) {
		this.contactName = contactName;
		notifyDataSetChanged();
	}

	@Override
	public int getItemViewType(int position) {
		return items.get(position).getType();
	}

	@Override
	public ViewHolder onCreateViewHolder(ViewGroup viewGroup, int type) {
		View v;

		// outgoing message (local)
		if (type == MSG_OUT) {
			v = LayoutInflater.from(viewGroup.getContext()).inflate(
					R.layout.list_item_msg_out, viewGroup, false);
			return new MessageHolder(v, type);
		} else if (type == INTRODUCTION_IN) {
			v = LayoutInflater.from(viewGroup.getContext()).inflate(
					R.layout.list_item_introduction_in, viewGroup, false);
			return new IntroductionHolder(v, type);
		} else if (type == INTRODUCTION_OUT) {
			v = LayoutInflater.from(viewGroup.getContext()).inflate(
					R.layout.list_item_msg_notice_out, viewGroup, false);
			return new IntroductionHolder(v, type);
		} else if (type == NOTICE_IN) {
			v = LayoutInflater.from(viewGroup.getContext()).inflate(
					R.layout.list_item_notice_in, viewGroup, false);
			return new NoticeHolder(v, type);
		} else if (type == NOTICE_OUT) {
			v = LayoutInflater.from(viewGroup.getContext()).inflate(
					R.layout.list_item_notice_out, viewGroup, false);
			return new NoticeHolder(v, type);
		} else if (type == FORUM_INVITATION_IN || type == BLOG_INVITATION_IN) {
			v = LayoutInflater.from(viewGroup.getContext()).inflate(
					R.layout.list_item_shareable_invitation_in, viewGroup,
					false);
			return new InvitationHolder(v, type);
		} else if (type == FORUM_INVITATION_OUT ||
				type == BLOG_INVITATION_OUT) {
			v = LayoutInflater.from(viewGroup.getContext()).inflate(
					R.layout.list_item_msg_notice_out, viewGroup, false);
			return new InvitationHolder(v, type);
		}
		// incoming message (non-local)
		else {
			v = LayoutInflater.from(viewGroup.getContext()).inflate(
					R.layout.list_item_msg_in, viewGroup, false);
			return new MessageHolder(v, type);
		}
	}

	@Override
	public void onBindViewHolder(ViewHolder ui, int position) {
		ConversationItem item = getItemAt(position);
		if (item instanceof ConversationMessageItem) {
			bindMessage((MessageHolder) ui, (ConversationMessageItem) item);
		} else if (item instanceof ConversationIntroductionOutItem) {
			bindIntroduction((IntroductionHolder) ui,
					(ConversationIntroductionOutItem) item, position);
		} else if (item instanceof ConversationIntroductionInItem) {
			bindIntroduction((IntroductionHolder) ui,
					(ConversationIntroductionInItem) item, position);
		} else if (item instanceof ConversationNoticeOutItem) {
			bindNotice((NoticeHolder) ui, (ConversationNoticeOutItem) item);
		} else if (item instanceof ConversationNoticeInItem) {
			bindNotice((NoticeHolder) ui, (ConversationNoticeInItem) item);
		} else if (item instanceof ConversationShareableInvitationOutItem) {
			bindInvitation((InvitationHolder) ui,
					(ConversationShareableInvitationOutItem) item);
		} else if (item instanceof ConversationShareableInvitationInItem) {
			bindInvitation((InvitationHolder) ui,
					(ConversationShareableInvitationInItem) item);
		} else {
			throw new IllegalArgumentException("Unhandled Conversation Item");
		}
	}

	private void bindMessage(MessageHolder ui, ConversationMessageItem item) {
		PrivateMessageHeader header = item.getHeader();

		if (item instanceof ConversationItem.OutgoingItem) {
			if (((OutgoingItem) item).isSeen()) {
				ui.status.setImageResource(R.drawable.message_delivered_white);
			} else if (((OutgoingItem) item).isSent()) {
				ui.status.setImageResource(R.drawable.message_sent_white);
			} else {
				ui.status.setImageResource(R.drawable.message_stored_white);
			}
		} else {
			if (item.getType() == MSG_IN_UNREAD) {
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
		} else if (header.getContentType().equals("text/plain")) {
			ui.body.setText(
					StringUtils.trim(StringUtils.fromUtf8(item.getBody())));
		} else {
			// TODO support other content types
		}

		long timestamp = header.getTimestamp();
		ui.date.setText(AndroidUtils.formatDate(ctx, timestamp));
	}

	private void bindIntroduction(IntroductionHolder ui,
			final ConversationIntroductionItem item, final int position) {

		final IntroductionRequest ir = item.getIntroductionRequest();
		int backgroundRes;

		String message = ir.getMessage();
		if (StringUtils.isNullOrEmpty(message)) {
			ui.message.setVisibility(GONE);
			if (item instanceof ConversationIntroductionOutItem) {
				backgroundRes = R.drawable.notice_out;
			} else {
				backgroundRes = R.drawable.notice_in;
			}
		} else {
			ui.message.setText(StringUtils.trim(message));
			ui.message.setVisibility(VISIBLE);
			if (item instanceof ConversationIntroductionOutItem) {
				backgroundRes = R.drawable.notice_out_bottom;
			} else {
				backgroundRes = R.drawable.notice_in_bottom;
			}
		}

		// Outgoing Introduction Request
		if (item instanceof ConversationIntroductionOutItem) {
			ui.text.setText(ctx.getString(R.string.introduction_request_sent,
					contactName, ir.getName()));
			ConversationIntroductionOutItem i =
					(ConversationIntroductionOutItem) item;
			if (i.isSeen()) {
				//noinspection ConstantConditions
				ui.status.setImageResource(R.drawable.message_delivered);
			} else if (i.isSent()) {
				//noinspection ConstantConditions
				ui.status.setImageResource(R.drawable.message_sent);
			} else {
				//noinspection ConstantConditions
				ui.status.setImageResource(R.drawable.message_stored);
			}
		}
		// Incoming Introduction Request (Answered)
		else if (item.wasAnswered()) {
			ui.text.setText(ctx.getString(
					R.string.introduction_request_answered_received,
					contactName, ir.getName()));
			ui.acceptButton.setVisibility(GONE);
			ui.declineButton.setVisibility(GONE);
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
				ui.acceptButton.setVisibility(GONE);
				ui.text.setText(ctx.getString(
						R.string.introduction_request_for_our_identity_received,
						contactName, ir.getName()));
			} else {
				ui.acceptButton.setVisibility(VISIBLE);
				ui.acceptButton.setOnClickListener(new View.OnClickListener() {
					@Override
					public void onClick(View v) {
						intro.respondToIntroduction(ir.getSessionId(), true);
						item.setAnswered(true);
						notifyItemChanged(position);
					}
				});
			}
			ui.declineButton.setVisibility(VISIBLE);
			ui.declineButton.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					intro.respondToIntroduction(ir.getSessionId(), false);
					item.setAnswered(true);
					notifyItemChanged(position);
				}
			});
		}
		ui.date.setText(AndroidUtils.formatDate(ctx, item.getTime()));
		ui.notice.setBackgroundResource(backgroundRes);
	}

	private void bindNotice(NoticeHolder ui, ConversationNoticeItem item) {
		ui.text.setText(item.getText());
		ui.date.setText(AndroidUtils.formatDate(ctx, item.getTime()));

		if (item instanceof ConversationNoticeOutItem) {
			ConversationNoticeOutItem n = (ConversationNoticeOutItem) item;
			if (n.isSeen()) {
				//noinspection ConstantConditions
				ui.status.setImageResource(R.drawable.message_delivered);
			} else if (n.isSent()) {
				//noinspection ConstantConditions
				ui.status.setImageResource(R.drawable.message_sent);
			} else {
				//noinspection ConstantConditions
				ui.status.setImageResource(R.drawable.message_stored);
			}
		}
	}

	private void bindInvitation(InvitationHolder ui,
			final ConversationShareableInvitationItem item) {

		final InvitationRequest ir = item.getInvitationRequest();
		String name = "";
		int receivedRes =  0, sentRes = 0, buttonRes = 0, backgroundRes;
		if (ir instanceof ForumInvitationRequest) {
			name = ((ForumInvitationRequest) ir).getForumName();
			receivedRes = R.string.forum_invitation_received;
			sentRes = R.string.forum_invitation_sent;
			buttonRes = R.string.forum_show_invitations;
		} else if (ir instanceof BlogInvitationRequest) {
			name = ((BlogInvitationRequest) ir).getBlogAuthorName();
			receivedRes = R.string.blogs_sharing_invitation_received;
			sentRes = R.string.blogs_sharing_invitation_sent;
			buttonRes = R.string.blogs_sharing_show_invitations;
		}

		String message = ir.getMessage();
		if (StringUtils.isNullOrEmpty(message)) {
			ui.message.setVisibility(GONE);
			if (item instanceof ConversationShareableInvitationOutItem) {
				backgroundRes = R.drawable.notice_out;
			} else {
				backgroundRes = R.drawable.notice_in;
			}
		} else {
			ui.message.setVisibility(VISIBLE);
			ui.message.setText(StringUtils.trim(message));
			if (item instanceof ConversationShareableInvitationOutItem) {
				backgroundRes = R.drawable.notice_out_bottom;
			} else {
				backgroundRes = R.drawable.notice_in_bottom;
			}
		}

		// Outgoing Invitation
		if (item instanceof ConversationShareableInvitationOutItem) {
			ui.text.setText(ctx.getString(sentRes, name, contactName));
			ConversationShareableInvitationOutItem i =
					(ConversationShareableInvitationOutItem) item;
			if (i.isSeen()) {
				//noinspection ConstantConditions
				ui.status.setImageResource(R.drawable.message_delivered);
			} else if (i.isSent()) {
				//noinspection ConstantConditions
				ui.status.setImageResource(R.drawable.message_sent);
			} else {
				//noinspection ConstantConditions
				ui.status.setImageResource(R.drawable.message_stored);
			}
		}
		// Incoming Invitation
		else {
			ui.text.setText(ctx.getString(receivedRes, contactName, name));

			if (ir.isAvailable()) {
				final Class c = ir instanceof ForumInvitationRequest ?
						InvitationsForumActivity.class :
						InvitationsBlogActivity.class;
				ui.showInvitationsButton.setText(ctx.getString(buttonRes));
				ui.showInvitationsButton.setVisibility(VISIBLE);
				ui.showInvitationsButton
						.setOnClickListener(new View.OnClickListener() {
							@Override
							public void onClick(View v) {
								Intent i = new Intent(ctx, c);
								ctx.startActivity(i);
							}
						});
			} else {
				ui.showInvitationsButton.setVisibility(GONE);
			}
		}
		ui.date.setText(AndroidUtils.formatDate(ctx, item.getTime()));
		ui.notice.setBackgroundResource(backgroundRes);
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

	@Nullable
	ConversationItem getLastItem() {
		if (items.size() > 0) {
			return items.get(items.size() - 1);
		} else {
			return null;
		}
	}

	SparseArray<IncomingItem> getIncomingMessages() {
		SparseArray<IncomingItem> messages = new SparseArray<>();

		for (int i = 0; i < items.size(); i++) {
			ConversationItem item = items.get(i);
			if (item instanceof IncomingItem) {
				messages.put(i, (IncomingItem) item);
			}
		}
		return messages;
	}

	SparseArray<OutgoingItem> getOutgoingMessages() {
		SparseArray<OutgoingItem> messages = new SparseArray<>();

		for (int i = 0; i < items.size(); i++) {
			ConversationItem item = items.get(i);
			if (item instanceof OutgoingItem) {
				messages.put(i, (OutgoingItem) item);
			}
		}
		return messages;
	}

	SparseArray<ConversationMessageItem> getPrivateMessages() {
		SparseArray<ConversationMessageItem> messages = new SparseArray<>();

		for (int i = 0; i < items.size(); i++) {
			ConversationItem item = items.get(i);
			if (item instanceof ConversationMessageItem) {
				messages.put(i, (ConversationMessageItem) item);
			}
		}
		return messages;
	}

	private static class MessageHolder extends RecyclerView.ViewHolder {

		public ViewGroup layout;
		public TextView body;
		private TextView date;
		public ImageView status;

		private MessageHolder(View v, int type) {
			super(v);

			layout = (ViewGroup) v.findViewById(R.id.msgLayout);
			body = (TextView) v.findViewById(R.id.msgBody);
			date = (TextView) v.findViewById(R.id.msgTime);

			// outgoing message (local)
			if (type == MSG_OUT) {
				status = (ImageView) v.findViewById(R.id.msgStatus);
			}
		}
	}

	private static class IntroductionHolder extends RecyclerView.ViewHolder {

		private final TextView message;
		private final ViewGroup notice;
		private final TextView text;
		private final Button acceptButton;
		private final Button declineButton;
		private final TextView date;
		private final ImageView status;

		private IntroductionHolder(View v, int type) {
			super(v);

			message = (TextView) v.findViewById(R.id.msgBody);
			notice = (ViewGroup) v.findViewById(R.id.noticeLayout);
			text = (TextView) v.findViewById(R.id.introductionText);
			acceptButton = (Button) v.findViewById(R.id.acceptButton);
			declineButton = (Button) v.findViewById(R.id.declineButton);
			date = (TextView) v.findViewById(R.id.introductionTime);

			if (type == INTRODUCTION_OUT) {
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

		private NoticeHolder(View v, int type) {
			super(v);

			text = (TextView) v.findViewById(R.id.noticeText);
			date = (TextView) v.findViewById(R.id.noticeTime);

			if (type == NOTICE_OUT) {
				status = (ImageView) v.findViewById(R.id.noticeStatus);
			} else {
				status = null;
			}
		}
	}

	private static class InvitationHolder extends RecyclerView.ViewHolder {

		private final TextView message;
		private final View notice;
		private final TextView text;
		private final Button showInvitationsButton;
		private final TextView date;
		private final ImageView status;

		private InvitationHolder(View v, int type) {
			super(v);

			message = (TextView) v.findViewById(R.id.msgBody);
			text = (TextView) v.findViewById(R.id.introductionText);
			notice = v.findViewById(R.id.noticeLayout);
			showInvitationsButton = (Button) v.findViewById(R.id.showInvitationsButton);
			date = (TextView) v.findViewById(R.id.introductionTime);

			if (type == FORUM_INVITATION_OUT || type == BLOG_INVITATION_OUT) {
				status = (ImageView) v.findViewById(R.id.introductionStatus);
			} else {
				status = null;
			}
		}
	}

	interface IntroductionHandler {
		void respondToIntroduction(SessionId sessionId, boolean accept);
	}
}
