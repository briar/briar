package org.briarproject.briar.socialbackup;

import org.briarproject.bramble.api.contact.Contact;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.plugin.TransportId;
import org.briarproject.bramble.api.properties.TransportProperties;
import org.briarproject.briar.api.socialbackup.Shard;

import java.util.Map;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

@Immutable
@NotNullByDefault
class ContactData {

	private final Contact contact;
	private final Map<TransportId, TransportProperties> properties;
	@Nullable
	private final Shard shard;

	ContactData(Contact contact,
			Map<TransportId, TransportProperties> properties,
			@Nullable Shard shard) {
		this.contact = contact;
		this.properties = properties;
		this.shard = shard;
	}

	public Contact getContact() {
		return contact;
	}

	public Map<TransportId, TransportProperties> getProperties() {
		return properties;
	}

	@Nullable
	public Shard getShard() {
		return shard;
	}
}
