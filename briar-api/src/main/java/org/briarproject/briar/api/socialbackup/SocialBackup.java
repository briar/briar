package org.briarproject.briar.api.socialbackup;

import org.briarproject.bramble.api.identity.Identity;

import java.util.List;

public class SocialBackup {
	private Identity identity;
	private List<ContactData> contacts;
	private int version;

	public SocialBackup (Identity identity, List<ContactData> contacts, int version) {
		this.identity = identity;
		this.contacts = contacts;
		this.version = version;
	}

	public Identity getIdentity() {
		return identity;
	}

	public List<ContactData> getContacts() {
		return contacts;
	}

	public int getVersion() {
		return version;
	}
}
