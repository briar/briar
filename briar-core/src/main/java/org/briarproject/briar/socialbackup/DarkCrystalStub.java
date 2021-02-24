package org.briarproject.briar.socialbackup;

import org.briarproject.bramble.api.crypto.SecretKey;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.briar.api.socialbackup.Shard;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import javax.inject.Inject;

import static org.briarproject.briar.socialbackup.SocialBackupConstants.SECRET_ID_BYTES;

@NotNullByDefault
class DarkCrystalStub implements DarkCrystal {

	@Inject
	DarkCrystalStub() {
	}

	@Override
	public List<Shard> createShards(SecretKey secret, int numShards,
			int threshold) {
		Random random = new Random();
		byte[] secretId = new byte[SECRET_ID_BYTES];
		random.nextBytes(secretId);
		List<Shard> shards = new ArrayList<>(numShards);
		for (int i = 0; i < numShards; i++) {
			byte[] shard = new byte[123];
			random.nextBytes(shard);
			shards.add(new Shard(secretId, shard));
		}
		return shards;
	}
}
