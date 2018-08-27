package org.briarproject.briar.headless.contact;

import org.briarproject.bramble.api.contact.Contact;
import org.briarproject.bramble.api.contact.ContactManager;
import org.briarproject.bramble.api.db.DbException;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import javax.annotation.concurrent.Immutable;
import javax.inject.Inject;
import javax.inject.Singleton;

import io.javalin.Context;

@Immutable
@Singleton
@NotNullByDefault
public class ContactController {

	private final ContactManager contactManager;

	@Inject
	public ContactController(ContactManager contactManager) {
		this.contactManager = contactManager;
	}

	public Context list(Context ctx) throws DbException {
		Collection<Contact> contacts = contactManager.getActiveContacts();
		List<OutputContact> outputContacts = new ArrayList<>(contacts.size());
		for (Contact c : contacts) {
			outputContacts.add(new OutputContact(c));
		}
		return ctx.json(outputContacts);
	}

}
