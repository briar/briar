package org.briarproject.crypto;

import static org.briarproject.api.crypto.PasswordStrengthEstimator.QUITE_STRONG;

import org.briarproject.BriarTestCase;
import org.briarproject.api.crypto.PasswordStrengthEstimator;
import org.junit.Test;

public class PasswordStrengthEstimatorTest extends BriarTestCase {

	@Test
	public void testWeakPasswords() {
		PasswordStrengthEstimator e = new PasswordStrengthEstimatorImpl();
		assertTrue(e.estimateStrength("".toCharArray()) < QUITE_STRONG);
		assertTrue(e.estimateStrength("password".toCharArray()) < QUITE_STRONG);
		assertTrue(e.estimateStrength("letmein".toCharArray()) < QUITE_STRONG);
		assertTrue(e.estimateStrength("123456".toCharArray()) < QUITE_STRONG);
	}

	@Test
	public void testStrongPasswords() {
		PasswordStrengthEstimator e = new PasswordStrengthEstimatorImpl();
		// Industry standard
		assertTrue(e.estimateStrength("Tr0ub4dor&3".toCharArray())
				> QUITE_STRONG);
		assertTrue(e.estimateStrength("correcthorsebatterystaple".toCharArray())
				> QUITE_STRONG);
	}
}
