package org.briarproject.briar.headless.messaging;

import org.briarproject.bramble.api.FormatException;
import org.briarproject.bramble.api.contact.Contact;
import org.briarproject.bramble.api.contact.ContactId;
import org.briarproject.bramble.api.contact.ContactManager;
import org.briarproject.bramble.api.db.DbException;
import org.briarproject.bramble.api.db.NoSuchContactException;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.sync.Group;
import org.briarproject.bramble.api.system.Clock;
import org.briarproject.briar.api.messaging.MessagingManager;
import org.briarproject.briar.api.messaging.PrivateMessage;
import org.briarproject.briar.api.messaging.PrivateMessageFactory;
import org.briarproject.briar.api.messaging.PrivateMessageHeader;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;
import javax.inject.Inject;
import javax.inject.Singleton;

import io.javalin.Context;

import static org.briarproject.briar.api.messaging.MessagingConstants.MAX_PRIVATE_MESSAGE_BODY_LENGTH;

@Immutable
@Singleton
@NotNullByDefault
public class MessagingController {

	private final MessagingManager messagingManager;
	private final PrivateMessageFactory privateMessageFactory;
	private final ContactManager contactManager;
	private final Clock clock;

	@Inject
	public MessagingController(MessagingManager messagingManager,
			PrivateMessageFactory privateMessageFactory,
			ContactManager contactManager,
			Clock clock) {
		this.messagingManager = messagingManager;
		this.privateMessageFactory = privateMessageFactory;
		this.contactManager = contactManager;
		this.clock = clock;
	}

	public Context list(Context ctx) throws DbException {
		Contact contact = getContact(ctx);
		if (contact == null) return ctx.status(404);

		Collection<PrivateMessageHeader> headers =
				messagingManager.getMessageHeaders(contact.getId());
		List<OutputPrivateMessage> messages = new ArrayList<>(headers.size());
		for (PrivateMessageHeader header : headers) {
			String body = messagingManager.getMessageBody(header.getId());
			messages.add(new OutputPrivateMessage(header, body));
		}
		return ctx.json(messages);
	}

	public Context write(Context ctx) throws DbException, FormatException {
		Contact contact = getContact(ctx);
		if (contact == null) return ctx.status(404);

		String message = ctx.formParam("message");
		if (message == null || message.length() < 1)
			return ctx.status(500).result("Expecting Message text");
		if (message.length() > MAX_PRIVATE_MESSAGE_BODY_LENGTH)
			return ctx.status(500).result("Message text too large");

		Group group = messagingManager.getContactGroup(contact);
		long now = clock.currentTimeMillis();
		PrivateMessage m = privateMessageFactory
				.createPrivateMessage(group.getId(), now, message);

		messagingManager.addLocalMessage(m);
		return ctx.json(new OutputPrivateMessage(m, message));
	}

	@Nullable
	private Contact getContact(Context ctx) throws DbException {
		String contactString = ctx.param("contactId");
		if (contactString == null) return null;
		int contactInt = Integer.parseInt(contactString);
		ContactId contactId = new ContactId(contactInt);
		try {
			return contactManager.getContact(contactId);
		} catch (NoSuchContactException e) {
			return null;
		}
	}

}
