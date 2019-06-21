package org.briarproject.bramble.api.sync.event;

import org.briarproject.bramble.api.Consumable;
import org.briarproject.bramble.api.contact.ContactId;
import org.briarproject.bramble.api.event.Event;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.sync.MessageId;

import java.util.Collection;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

/**
 * An event that is broadcast when a message is offered by a contact and needs
 * to be requested.
 */
@Immutable
@NotNullByDefault
public class MessageToRequestEvent extends Event {

	private final ContactId contactId;
	private final Consumable<Collection<MessageId>> ids;

	public MessageToRequestEvent(ContactId contactId,
			Collection<MessageId> ids) {
		this.contactId = contactId;
		this.ids = new Consumable<>(ids);
	}

	public ContactId getContactId() {
		return contactId;
	}

	@Nullable
	public Collection<MessageId> consumeIds() {
		return ids.consume();
	}
}
