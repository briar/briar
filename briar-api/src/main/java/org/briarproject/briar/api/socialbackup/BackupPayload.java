package org.briarproject.briar.socialbackup;

import org.briarproject.bramble.api.Bytes;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;

import javax.annotation.concurrent.Immutable;

@Immutable
@NotNullByDefault
class BackupPayload extends Bytes {

	BackupPayload(byte[] payload) {
		super(payload);
	}
}
