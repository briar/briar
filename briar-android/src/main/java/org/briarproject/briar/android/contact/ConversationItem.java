package org.briarproject.briar.android.contact;

import android.content.Context;
import android.support.annotation.LayoutRes;

import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.sync.GroupId;
import org.briarproject.bramble.api.sync.MessageId;
import org.briarproject.briar.api.messaging.PrivateMessageHeader;
import org.briarproject.briar.api.messaging.PrivateRequest;
import org.briarproject.briar.api.messaging.PrivateResponse;

import javax.annotation.Nullable;
import javax.annotation.concurrent.NotThreadSafe;

@NotThreadSafe
@NotNullByDefault
abstract class ConversationItem {

	@Nullable
	protected String body;
	private final MessageId id;
	private final GroupId groupId;
	private final long time;
	private boolean read;

	ConversationItem(MessageId id, GroupId groupId, @Nullable String body,
			long time, boolean read) {
		this.id = id;
		this.groupId = groupId;
		this.body = body;
		this.time = time;
		this.read = read;
	}

	MessageId getId() {
		return id;
	}

	GroupId getGroupId() {
		return groupId;
	}

	void setBody(String body) {
		this.body = body;
	}

	@Nullable
	public String getBody() {
		return body;
	}

	long getTime() {
		return time;
	}

	public boolean isRead() {
		return read;
	}

	abstract public boolean isIncoming();

	@LayoutRes
	abstract public int getLayout();

	static ConversationItem from(PrivateMessageHeader h) {
		if (h.isLocal()) {
			return new ConversationMessageOutItem(h);
		} else {
			return new ConversationMessageInItem(h);
		}
	}

	static ConversationItem from(Context ctx, String contactName,
			PrivateMessageHeader h) {
		if (h.isLocal()) {
			return fromLocal(ctx, contactName, h);
		} else {
			return fromRemote(ctx, contactName, h);
		}
	}

	private static ConversationItem fromLocal(Context ctx, String contactName,
			PrivateMessageHeader h) {
		if (h instanceof PrivateRequest) {
			PrivateRequest r = (PrivateRequest) h;
			return new ConversationNoticeOutItem(ctx, contactName, r);
		} else if (h instanceof PrivateResponse) {
			PrivateResponse r = (PrivateResponse) h;
			return new ConversationNoticeOutItem(ctx, contactName, r);
		} else {
			return new ConversationMessageOutItem(h);
		}
	}

	private static ConversationItem fromRemote(Context ctx, String contactName,
			PrivateMessageHeader h) {
		if (h instanceof PrivateRequest) {
			PrivateRequest r = (PrivateRequest) h;
			return new ConversationRequestItem(ctx, contactName, r);
		} else if (h instanceof PrivateResponse) {
			PrivateResponse r = (PrivateResponse) h;
			return new ConversationNoticeInItem(ctx, contactName, r);
		} else {
			return new ConversationMessageInItem(h);
		}
	}

}
