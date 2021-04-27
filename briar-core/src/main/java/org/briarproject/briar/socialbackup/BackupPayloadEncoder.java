package org.briarproject.briar.socialbackup;

import org.briarproject.bramble.api.crypto.SecretKey;
import org.briarproject.bramble.api.identity.Identity;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.briar.api.socialbackup.BackupPayload;
import org.briarproject.briar.api.socialbackup.ContactData;

import java.util.List;

@NotNullByDefault
interface BackupPayloadEncoder {

	BackupPayload encodeBackupPayload(SecretKey secret, Identity identity,
			List<ContactData> contactData, int version);
}
