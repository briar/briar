package org.briarproject.bramble.crypto;

import org.briarproject.bramble.api.Bytes;
import org.briarproject.bramble.api.crypto.SecretKey;
import org.briarproject.bramble.api.transport.IncomingKeys;
import org.briarproject.bramble.api.transport.OutgoingKeys;
import org.briarproject.bramble.api.transport.TransportKeys;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertTrue;

class KeyDerivationTestUtils {

	static void assertAllDifferent(TransportKeys... transportKeys) {
		List<SecretKey> secretKeys = new ArrayList<>();
		for (TransportKeys k : transportKeys) {
			secretKeys.add(k.getPreviousIncomingKeys().getTagKey());
			secretKeys.add(k.getPreviousIncomingKeys().getHeaderKey());
			secretKeys.add(k.getCurrentIncomingKeys().getTagKey());
			secretKeys.add(k.getCurrentIncomingKeys().getHeaderKey());
			secretKeys.add(k.getNextIncomingKeys().getTagKey());
			secretKeys.add(k.getNextIncomingKeys().getHeaderKey());
			secretKeys.add(k.getCurrentOutgoingKeys().getTagKey());
			secretKeys.add(k.getCurrentOutgoingKeys().getHeaderKey());
		}
		assertAllDifferent(secretKeys);
	}

	static void assertAllDifferent(List<SecretKey> keys) {
		Set<Bytes> set = new HashSet<>();
		for (SecretKey k : keys) assertTrue(set.add(new Bytes(k.getBytes())));
	}

	static void assertMatches(IncomingKeys in, OutgoingKeys out) {
		assertArrayEquals(in.getTagKey().getBytes(),
				out.getTagKey().getBytes());
		assertArrayEquals(in.getHeaderKey().getBytes(),
				out.getHeaderKey().getBytes());
	}
}
