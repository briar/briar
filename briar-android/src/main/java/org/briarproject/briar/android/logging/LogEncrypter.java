package org.briarproject.briar.android.logging;

import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.util.AndroidUtils;

import androidx.annotation.Nullable;

@NotNullByDefault
public interface LogEncrypter {
	/**
	 * Writes encrypted log records to {@link AndroidUtils#getTemporaryLogFile}
	 * and returns the encryption key if everything went fine.
	 */
	@Nullable
	byte[] encryptLogs();
}
