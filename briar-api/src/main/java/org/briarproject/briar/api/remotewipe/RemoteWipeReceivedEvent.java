package org.briarproject.briar.api.remotewipe;

import org.briarproject.bramble.api.contact.ContactId;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.briar.api.conversation.event.ConversationMessageReceivedEvent;

import jdk.nashorn.internal.ir.annotations.Immutable;

@Immutable
@NotNullByDefault
public class RemoteWipeReceivedEvent
		extends ConversationMessageReceivedEvent<RemoteWipeMessageHeader> {

	public RemoteWipeReceivedEvent(RemoteWipeMessageHeader messageHeader,
			ContactId contactId) {
		super(messageHeader, contactId);
	}

}
