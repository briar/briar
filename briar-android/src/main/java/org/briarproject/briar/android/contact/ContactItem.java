package org.briarproject.briar.android.contact;

import org.briarproject.bramble.api.contact.Contact;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.plugin.ConnectionStatus;

import javax.annotation.concurrent.NotThreadSafe;

import static org.briarproject.bramble.api.plugin.ConnectionStatus.DISCONNECTED;

@NotThreadSafe
@NotNullByDefault
public class ContactItem {

	private final Contact contact;
	private ConnectionStatus status;

	public ContactItem(Contact contact) {
		this(contact, DISCONNECTED);
	}

	public ContactItem(Contact contact, ConnectionStatus status) {
		this.contact = contact;
		this.status = status;
	}

	public Contact getContact() {
		return contact;
	}

	ConnectionStatus getConnectionStatus() {
		return status;
	}

	void setConnectionStatus(ConnectionStatus status) {
		this.status = status;
	}

}
