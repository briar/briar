package org.briarproject.api.privategroup;

import org.briarproject.api.clients.ThreadedMessage;
import org.briarproject.api.identity.Author;
import org.briarproject.api.nullsafety.NotNullByDefault;
import org.briarproject.api.sync.Message;
import org.briarproject.api.sync.MessageId;
import org.jetbrains.annotations.Nullable;

import javax.annotation.concurrent.Immutable;

@Immutable
@NotNullByDefault
public class GroupMessage extends ThreadedMessage {

	public GroupMessage(Message message, @Nullable MessageId parent,
			Author member) {
		super(message, parent, member);
	}

	public Author getMember() {
		return super.getAuthor();
	}

}
