package org.briarproject.briar.socialbackup;

import org.briarproject.bramble.api.crypto.SecretKey;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.briar.api.socialbackup.Shard;

import java.util.List;

@NotNullByDefault
interface DarkCrystal {

	List<Shard> createShards(SecretKey secret, int shards, int threshold);
}
