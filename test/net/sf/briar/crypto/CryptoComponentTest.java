package net.sf.briar.crypto;

import junit.framework.TestCase;
import net.sf.briar.api.crypto.CryptoComponent;

import org.junit.Test;

import com.google.inject.Guice;
import com.google.inject.Injector;

public class CryptoComponentTest extends TestCase {

	private final CryptoComponent crypto;

	public CryptoComponentTest() {
		super();
		Injector i = Guice.createInjector(new CryptoModule());
		crypto = i.getInstance(CryptoComponent.class);
	}

	@Test
	public void testKeyDerivation() {
		// Create matching secrets: one for Alice, one for Bob
		byte[] aliceSecret = new byte[123];
		aliceSecret[SharedSecret.IV_BYTES] = (byte) 1;
		byte[] bobSecret = new byte[123];
		// Check that Alice's incoming keys match Bob's outgoing keys
		assertEquals(crypto.deriveIncomingMacKey(aliceSecret),
				crypto.deriveOutgoingMacKey(bobSecret));
		assertEquals(crypto.deriveIncomingFrameKey(aliceSecret),
				crypto.deriveOutgoingFrameKey(bobSecret));
		assertEquals(crypto.deriveIncomingIvKey(aliceSecret),
				crypto.deriveOutgoingIvKey(bobSecret));
		// Check that Alice's outgoing keys match Bob's incoming keys
		assertEquals(crypto.deriveOutgoingMacKey(aliceSecret),
				crypto.deriveIncomingMacKey(bobSecret));
		assertEquals(crypto.deriveOutgoingFrameKey(aliceSecret),
				crypto.deriveIncomingFrameKey(bobSecret));
		assertEquals(crypto.deriveOutgoingIvKey(aliceSecret),
				crypto.deriveIncomingIvKey(bobSecret));
		// Check that Alice's incoming and outgoing keys are different
		assertFalse(crypto.deriveIncomingMacKey(aliceSecret).equals(
				crypto.deriveOutgoingMacKey(aliceSecret)));
		assertFalse(crypto.deriveIncomingFrameKey(aliceSecret).equals(
				crypto.deriveOutgoingFrameKey(aliceSecret)));
		assertFalse(crypto.deriveIncomingIvKey(aliceSecret).equals(
				crypto.deriveOutgoingIvKey(aliceSecret)));
	}
}
