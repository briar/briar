package org.briarproject.briar.android.socialbackup;
import org.briarproject.bramble.api.crypto.SecretKey;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.briar.api.socialbackup.Shard;
import org.briarproject.briar.api.socialbackup.DarkCrystal;
import org.magmacollective.darkcrystal.secretsharingwrapper.SecretSharingWrapper;

import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

import javax.inject.Inject;

import dagger.Provides;

import static org.briarproject.briar.socialbackup.SocialBackupConstants.SECRET_ID_BYTES;

@NotNullByDefault
public class DarkCrystalImpl implements DarkCrystal {

    @Inject
	DarkCrystalImpl() {
	}

	@Override
	public List<Shard> createShards(SecretKey secret, int numShards,
			int threshold) {
		Random random = new Random();
		byte[] secretId = new byte[SECRET_ID_BYTES];
		random.nextBytes(secretId);
		List<byte[]> shardsBytes = SecretSharingWrapper.share(secret.getBytes(), numShards, threshold);
		List<Shard> shards = new ArrayList<>(numShards);
		for (byte[] shardBytes : shardsBytes) {
			shards.add(new Shard(secretId, shardBytes));
		}
		return shards;
	}

	@Override
	public SecretKey combineShards(List<Shard> shards) throws
			GeneralSecurityException {
		// Check each shard has the same secret Id
		byte[] secretId = shards.get(0).getSecretId();
		for (Shard shard : shards) {
			if (!Arrays.equals(shard.getSecretId(), secretId)) throw new GeneralSecurityException();
		}
        List<byte[]> shardsBytes = new ArrayList<>(shards.size());
		for (Shard shard : shards) {
			shardsBytes.add(shard.getShard());
		}
		byte[] secretBytes = SecretSharingWrapper.combine(shardsBytes);
		return new SecretKey(secretBytes);
	}
}
