package org.briarproject.briar.api.socialbackup;

import org.briarproject.bramble.api.Bytes;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;

import javax.annotation.concurrent.Immutable;

@Immutable
@NotNullByDefault
public class BackupPayload extends Bytes {

	public BackupPayload(byte[] payload) {
		super(payload);
	}
}
