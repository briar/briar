package org.briarproject.bramble.contact;

import org.briarproject.bramble.api.FormatException;
import org.briarproject.bramble.api.contact.PendingContact;

interface PendingContactFactory {

	PendingContact createPendingContact(String link, String alias)
			throws FormatException;
}
