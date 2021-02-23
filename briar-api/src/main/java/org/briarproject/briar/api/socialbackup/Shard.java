package org.briarproject.briar.api.socialbackup;

import org.briarproject.bramble.api.nullsafety.NotNullByDefault;

import javax.annotation.concurrent.Immutable;

@Immutable
@NotNullByDefault
public class Shard {

	private final byte[] secretId, shard;
	private final int numShards, threshold;

	public Shard(byte[] secretId, int numShards, int threshold,
			byte[] shard) {
		this.secretId = secretId;
		this.numShards = numShards;
		this.threshold = threshold;
		this.shard = shard;
	}

	public byte[] getSecretId() {
		return secretId;
	}

	public int getNumShards() {
		return numShards;
	}

	public int getThreshold() {
		return threshold;
	}

	public byte[] getShard() {
		return shard;
	}
}
