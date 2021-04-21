package org.briarproject.briar.socialbackup;

import org.briarproject.bramble.api.identity.Identity;

import java.util.List;

public class SocialBackup {
	private Identity identity;
	private List<org.briarproject.briar.api.socialbackup.ContactData> contacts;
	private int version;

	SocialBackup (Identity identity, List<org.briarproject.briar.api.socialbackup.ContactData> contacts, int version) {
		this.identity = identity;
		this.contacts = contacts;
		this.version = version;
	}

	public Identity getIdentity() {
		return identity;
	}

	public List<org.briarproject.briar.api.socialbackup.ContactData> getContacts() {
		return contacts;
	}

	public int getVersion() {
		return version;
	}
}
