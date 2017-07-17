package org.briarproject.bramble.crypto;

import org.briarproject.bramble.api.Bytes;
import org.briarproject.bramble.api.crypto.CryptoComponent;
import org.briarproject.bramble.api.crypto.SecretKey;
import org.briarproject.bramble.test.BrambleTestCase;
import org.briarproject.bramble.test.TestSecureRandomProvider;
import org.briarproject.bramble.test.TestUtils;
import org.junit.Test;

import java.util.HashSet;
import java.util.Set;

import static junit.framework.TestCase.assertTrue;
import static org.briarproject.bramble.api.transport.TransportConstants.PROTOCOL_VERSION;
import static org.briarproject.bramble.api.transport.TransportConstants.TAG_LENGTH;

public class TagEncodingTest extends BrambleTestCase {

	private final CryptoComponent crypto;
	private final SecretKey tagKey;
	private final long streamNumber = 1234567890;

	public TagEncodingTest() {
		crypto = new CryptoComponentImpl(new TestSecureRandomProvider());
		tagKey = TestUtils.getSecretKey();
	}

	@Test
	public void testKeyAffectsTag() throws Exception {
		Set<Bytes> set = new HashSet<Bytes>();
		for (int i = 0; i < 100; i++) {
			byte[] tag = new byte[TAG_LENGTH];
			SecretKey tagKey = TestUtils.getSecretKey();
			crypto.encodeTag(tag, tagKey, PROTOCOL_VERSION, streamNumber);
			assertTrue(set.add(new Bytes(tag)));
		}
	}

	@Test
	public void testProtocolVersionAffectsTag() throws Exception {
		Set<Bytes> set = new HashSet<Bytes>();
		for (int i = 0; i < 100; i++) {
			byte[] tag = new byte[TAG_LENGTH];
			crypto.encodeTag(tag, tagKey, PROTOCOL_VERSION + i, streamNumber);
			assertTrue(set.add(new Bytes(tag)));
		}
	}

	@Test
	public void testStreamNumberAffectsTag() throws Exception {
		Set<Bytes> set = new HashSet<Bytes>();
		for (int i = 0; i < 100; i++) {
			byte[] tag = new byte[TAG_LENGTH];
			crypto.encodeTag(tag, tagKey, PROTOCOL_VERSION, streamNumber + i);
			assertTrue(set.add(new Bytes(tag)));
		}
	}
}
