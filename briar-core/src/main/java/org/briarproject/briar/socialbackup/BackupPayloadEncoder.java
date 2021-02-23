package org.briarproject.briar.socialbackup;

import org.briarproject.bramble.api.contact.Contact;
import org.briarproject.bramble.api.crypto.SecretKey;
import org.briarproject.bramble.api.identity.Identity;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.plugin.TransportId;
import org.briarproject.bramble.api.properties.TransportProperties;

import java.util.List;
import java.util.Map;

@NotNullByDefault
interface BackupPayloadEncoder {

	BackupPayload encodeBackupPayload(SecretKey secret, Identity identity,
			List<Contact> contacts,
			List<Map<TransportId, TransportProperties>> properties,
			int version);
}
