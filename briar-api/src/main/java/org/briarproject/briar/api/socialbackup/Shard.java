package org.briarproject.briar.api.socialbackup;

import org.briarproject.bramble.api.nullsafety.NotNullByDefault;

import java.util.Arrays;

import javax.annotation.concurrent.Immutable;

@Immutable
@NotNullByDefault
public class Shard {

	private final byte[] secretId, shard;

	public Shard(byte[] secretId, byte[] shard) {
		this.secretId = secretId;
		this.shard = shard;
	}

	public byte[] getSecretId() {
		return secretId;
	}

	public byte[] getShard() {
		return shard;
	}

	public boolean equals(Shard otherShard) {
		return Arrays.equals(secretId, otherShard.getSecretId()) &&
				Arrays.equals(shard, otherShard.getShard());
	}
}
