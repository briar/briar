package org.briarproject.briar.socialbackup;

import org.briarproject.bramble.api.FormatException;
import org.briarproject.bramble.api.crypto.SecretKey;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.briar.api.socialbackup.BackupPayload;

import java.security.GeneralSecurityException;

@NotNullByDefault
public interface BackupPayloadDecoder {
	SocialBackup decodeBackupPayload(
			SecretKey secret,
			BackupPayload backupPayload, byte[] nonce) throws FormatException,
			GeneralSecurityException;
}
