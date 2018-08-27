package org.briarproject.briar.headless.contact;

import org.briarproject.bramble.api.contact.Contact;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.identity.OutputAuthor;

import javax.annotation.concurrent.Immutable;

@Immutable
@NotNullByDefault
@SuppressWarnings("WeakerAccess")
public class OutputContact {

	public final int id;
	public final OutputAuthor author;
	public final boolean verified;

	public OutputContact(Contact c) {
		this.id = c.getId().getInt();
		this.author = new OutputAuthor(c.getAuthor());
		this.verified = c.isVerified();
	}

}
