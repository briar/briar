package org.briarproject.briar.api.socialbackup;

import org.briarproject.bramble.api.crypto.SecretKey;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;

import java.security.GeneralSecurityException;
import java.util.List;

@NotNullByDefault
public
interface DarkCrystal {

	List<Shard> createShards(SecretKey secret, int shards, int threshold);
	SecretKey combineShards(List<Shard> shards) throws GeneralSecurityException;
}
