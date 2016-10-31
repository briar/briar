package org.briarproject.api.privategroup;

import org.briarproject.api.clients.BaseMessage;
import org.briarproject.api.identity.Author;
import org.briarproject.api.nullsafety.NotNullByDefault;
import org.briarproject.api.sync.Message;
import org.briarproject.api.sync.MessageId;
import org.jetbrains.annotations.Nullable;

import javax.annotation.concurrent.Immutable;

@Immutable
@NotNullByDefault
public class GroupMessage extends BaseMessage {

	private final Author member;

	public GroupMessage(Message message, @Nullable MessageId parent,
			Author member) {
		super(message, parent);
		this.member = member;
	}

	public Author getMember() {
		return member;
	}

}
