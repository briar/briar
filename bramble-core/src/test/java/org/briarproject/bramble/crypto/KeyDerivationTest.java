package org.briarproject.bramble.crypto;

import org.briarproject.bramble.api.Bytes;
import org.briarproject.bramble.api.crypto.CryptoComponent;
import org.briarproject.bramble.api.crypto.SecretKey;
import org.briarproject.bramble.api.plugin.TransportId;
import org.briarproject.bramble.api.transport.TransportKeys;
import org.briarproject.bramble.test.BrambleTestCase;
import org.briarproject.bramble.test.TestSecureRandomProvider;
import org.briarproject.bramble.test.TestUtils;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class KeyDerivationTest extends BrambleTestCase {

	private final TransportId transportId = new TransportId("id");
	private final CryptoComponent crypto;
	private final SecretKey master;

	public KeyDerivationTest() {
		crypto = new CryptoComponentImpl(new TestSecureRandomProvider());
		master = TestUtils.getSecretKey();
	}

	@Test
	public void testKeysAreDistinct() {
		TransportKeys k = crypto.deriveTransportKeys(transportId, master,
				123, true);
		assertAllDifferent(k);
	}

	@Test
	public void testCurrentKeysMatchCurrentKeysOfContact() {
		// Start in rotation period 123
		TransportKeys kA = crypto.deriveTransportKeys(transportId, master,
				123, true);
		TransportKeys kB = crypto.deriveTransportKeys(transportId, master,
				123, false);
		// Alice's incoming keys should equal Bob's outgoing keys
		assertArrayEquals(kA.getCurrentIncomingKeys().getTagKey().getBytes(),
				kB.getCurrentOutgoingKeys().getTagKey().getBytes());
		assertArrayEquals(kA.getCurrentIncomingKeys().getHeaderKey().getBytes(),
				kB.getCurrentOutgoingKeys().getHeaderKey().getBytes());
		// Alice's outgoing keys should equal Bob's incoming keys
		assertArrayEquals(kA.getCurrentOutgoingKeys().getTagKey().getBytes(),
				kB.getCurrentIncomingKeys().getTagKey().getBytes());
		assertArrayEquals(kA.getCurrentOutgoingKeys().getHeaderKey().getBytes(),
				kB.getCurrentIncomingKeys().getHeaderKey().getBytes());
		// Rotate into the future
		kA = crypto.rotateTransportKeys(kA, 456);
		kB = crypto.rotateTransportKeys(kB, 456);
		// Alice's incoming keys should equal Bob's outgoing keys
		assertArrayEquals(kA.getCurrentIncomingKeys().getTagKey().getBytes(),
				kB.getCurrentOutgoingKeys().getTagKey().getBytes());
		assertArrayEquals(kA.getCurrentIncomingKeys().getHeaderKey().getBytes(),
				kB.getCurrentOutgoingKeys().getHeaderKey().getBytes());
		// Alice's outgoing keys should equal Bob's incoming keys
		assertArrayEquals(kA.getCurrentOutgoingKeys().getTagKey().getBytes(),
				kB.getCurrentIncomingKeys().getTagKey().getBytes());
		assertArrayEquals(kA.getCurrentOutgoingKeys().getHeaderKey().getBytes(),
				kB.getCurrentIncomingKeys().getHeaderKey().getBytes());
	}

	@Test
	public void testPreviousKeysMatchPreviousKeysOfContact() {
		// Start in rotation period 123
		TransportKeys kA = crypto.deriveTransportKeys(transportId, master,
				123, true);
		TransportKeys kB = crypto.deriveTransportKeys(transportId, master,
				123, false);
		// Compare Alice's previous keys in period 456 with Bob's current keys
		// in period 455
		kA = crypto.rotateTransportKeys(kA, 456);
		kB = crypto.rotateTransportKeys(kB, 455);
		// Alice's previous incoming keys should equal Bob's outgoing keys
		assertArrayEquals(kA.getPreviousIncomingKeys().getTagKey().getBytes(),
				kB.getCurrentOutgoingKeys().getTagKey().getBytes());
		assertArrayEquals(kA.getPreviousIncomingKeys().getHeaderKey().getBytes(),
				kB.getCurrentOutgoingKeys().getHeaderKey().getBytes());
		// Compare Alice's current keys in period 456 with Bob's previous keys
		// in period 457
		kB = crypto.rotateTransportKeys(kB, 457);
		// Alice's outgoing keys should equal Bob's previous incoming keys
		assertArrayEquals(kA.getCurrentOutgoingKeys().getTagKey().getBytes(),
				kB.getPreviousIncomingKeys().getTagKey().getBytes());
		assertArrayEquals(kA.getCurrentOutgoingKeys().getHeaderKey().getBytes(),
				kB.getPreviousIncomingKeys().getHeaderKey().getBytes());
	}

	@Test
	public void testNextKeysMatchNextKeysOfContact() {
		// Start in rotation period 123
		TransportKeys kA = crypto.deriveTransportKeys(transportId, master,
				123, true);
		TransportKeys kB = crypto.deriveTransportKeys(transportId, master,
				123, false);
		// Compare Alice's current keys in period 456 with Bob's next keys in
		// period 455
		kA = crypto.rotateTransportKeys(kA, 456);
		kB = crypto.rotateTransportKeys(kB, 455);
		// Alice's outgoing keys should equal Bob's next incoming keys
		assertArrayEquals(kA.getCurrentOutgoingKeys().getTagKey().getBytes(),
				kB.getNextIncomingKeys().getTagKey().getBytes());
		assertArrayEquals(kA.getCurrentOutgoingKeys().getHeaderKey().getBytes(),
				kB.getNextIncomingKeys().getHeaderKey().getBytes());
		// Compare Alice's next keys in period 456 with Bob's current keys
		// in period 457
		kB = crypto.rotateTransportKeys(kB, 457);
		// Alice's next incoming keys should equal Bob's outgoing keys
		assertArrayEquals(kA.getNextIncomingKeys().getTagKey().getBytes(),
				kB.getCurrentOutgoingKeys().getTagKey().getBytes());
		assertArrayEquals(kA.getNextIncomingKeys().getHeaderKey().getBytes(),
				kB.getCurrentOutgoingKeys().getHeaderKey().getBytes());
	}

	@Test
	public void testMasterKeyAffectsOutput() {
		SecretKey master1 = TestUtils.getSecretKey();
		assertFalse(Arrays.equals(master.getBytes(), master1.getBytes()));
		TransportKeys k = crypto.deriveTransportKeys(transportId, master,
				123, true);
		TransportKeys k1 = crypto.deriveTransportKeys(transportId, master1,
				123, true);
		assertAllDifferent(k, k1);
	}

	@Test
	public void testTransportIdAffectsOutput() {
		TransportId transportId1 = new TransportId("id1");
		assertFalse(transportId.getString().equals(transportId1.getString()));
		TransportKeys k = crypto.deriveTransportKeys(transportId, master,
				123, true);
		TransportKeys k1 = crypto.deriveTransportKeys(transportId1, master,
				123, true);
		assertAllDifferent(k, k1);
	}

	private void assertAllDifferent(TransportKeys... transportKeys) {
		List<SecretKey> secretKeys = new ArrayList<SecretKey>();
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

	private void assertAllDifferent(List<SecretKey> keys) {
		Set<Bytes> set = new HashSet<Bytes>();
		for (SecretKey k : keys) assertTrue(set.add(new Bytes(k.getBytes())));
	}
}
