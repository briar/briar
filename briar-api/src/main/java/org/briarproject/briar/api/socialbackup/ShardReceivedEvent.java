package org.briarproject.briar.api.socialbackup;

import org.briarproject.bramble.api.contact.ContactId;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.briar.api.conversation.event.ConversationMessageReceivedEvent;

import jdk.nashorn.internal.ir.annotations.Immutable;

@Immutable
@NotNullByDefault
public class ShardReceivedEvent
		extends ConversationMessageReceivedEvent<ShardMessageHeader> {

	public ShardReceivedEvent(ShardMessageHeader shardMessageHeader,
			ContactId contactId) {
		super(shardMessageHeader, contactId);
	}

}
