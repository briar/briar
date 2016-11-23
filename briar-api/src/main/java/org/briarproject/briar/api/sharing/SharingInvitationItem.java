package org.briarproject.briar.api.sharing;

import org.briarproject.bramble.api.contact.Contact;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;

import java.util.Collection;

import javax.annotation.concurrent.Immutable;

@Immutable
@NotNullByDefault
public class SharingInvitationItem extends InvitationItem<Shareable> {

	private final Collection<Contact> newSharers;

	public SharingInvitationItem(Shareable shareable, boolean subscribed,
			Collection<Contact> newSharers) {
		super(shareable, subscribed);

		this.newSharers = newSharers;
	}

	public Collection<Contact> getNewSharers() {
		return newSharers;
	}

}
