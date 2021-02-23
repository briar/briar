package org.briarproject.briar.api.socialbackup;

import org.briarproject.bramble.api.crypto.SecretKey;
import org.briarproject.bramble.api.identity.Author;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;

import java.util.List;

import javax.annotation.concurrent.Immutable;

@Immutable
@NotNullByDefault
public class BackupMetadata {

	private final SecretKey secret;
	private final List<Author> custodians;
	private final int threshold, version;

	public BackupMetadata(SecretKey secret, List<Author> custodians,
			int threshold, int version) {
		this.secret = secret;
		this.custodians = custodians;
		this.threshold = threshold;
		this.version = version;
	}

	public SecretKey getSecret() {
		return secret;
	}

	public List<Author> getCustodians() {
		return custodians;
	}

	public int getThreshold() {
		return threshold;
	}

	public int getVersion() {
		return version;
	}
}
