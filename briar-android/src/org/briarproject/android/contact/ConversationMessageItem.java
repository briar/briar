package org.briarproject.android.contact;

import org.briarproject.api.messaging.PrivateMessageHeader;
import org.briarproject.api.sync.MessageId;

// This class is not thread-safe
public class ConversationMessageItem extends ConversationItem implements
		ConversationItem.OutgoingItem, ConversationItem.IncomingItem {

	private final PrivateMessageHeader header;
	private byte[] body;
	private boolean sent, seen, read;

	public ConversationMessageItem(PrivateMessageHeader header) {
		super(header.getId(), header.getTimestamp());

		this.header = header;
		body = null;
		sent = header.isSent();
		seen = header.isSeen();
		read = header.isRead();
	}

	@Override
	int getType() {
		if (getHeader().isLocal()) return MSG_OUT;
		if (getHeader().isRead()) return MSG_IN;
		return MSG_IN_UNREAD;
	}

	PrivateMessageHeader getHeader() {
		return header;
	}

	byte[] getBody() {
		return body;
	}

	void setBody(byte[] body) {
		this.body = body;
	}

	@Override
	public boolean isSent() {
		return sent;
	}

	@Override
	public void setSent(boolean sent) {
		this.sent = sent;
	}

	@Override
	public boolean isSeen() {
		return seen;
	}

	@Override
	public void setSeen(boolean seen) {
		this.seen = seen;
	}

	@Override
	public boolean isRead() {
		return read;
	}

	@Override
	public void setRead(boolean read) {
		this.read = read;
	}

}
