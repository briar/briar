package org.briarproject.briar.api.socialbackup;

import org.briarproject.bramble.api.identity.Identity;
import org.briarproject.bramble.api.plugin.TransportId;
import org.briarproject.bramble.api.properties.TransportProperties;

import java.util.List;
import java.util.Map;

public class SocialBackup {
	private Identity identity;
	private List<ContactData> contacts;
	private Map<TransportId, TransportProperties> localTransportProperties;
	private int version;

	public SocialBackup (Identity identity, List<ContactData> contacts, Map<TransportId, TransportProperties> localTransportProperties, int version) {
		this.identity = identity;
		this.contacts = contacts;
		this.localTransportProperties = localTransportProperties;
		this.version = version;
	}

	public Identity getIdentity() {
		return identity;
	}

	public List<ContactData> getContacts() {
		return contacts;
	}

	public Map<TransportId, TransportProperties> getLocalTransportProperties() {
		return localTransportProperties;
	}

	public int getVersion() {
		return version;
	}
}
