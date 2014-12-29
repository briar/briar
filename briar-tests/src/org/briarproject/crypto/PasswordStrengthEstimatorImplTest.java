package org.briarproject.crypto;

import static org.briarproject.api.crypto.PasswordStrengthEstimator.QUITE_STRONG;

import org.briarproject.BriarTestCase;
import org.briarproject.api.crypto.PasswordStrengthEstimator;
import org.junit.Test;

public class PasswordStrengthEstimatorImplTest extends BriarTestCase {

	@Test
	public void testWeakPasswords() {
		PasswordStrengthEstimator e = new PasswordStrengthEstimatorImpl();
		assertTrue(e.estimateStrength("") < QUITE_STRONG);
		assertTrue(e.estimateStrength("password") < QUITE_STRONG);
		assertTrue(e.estimateStrength("letmein") < QUITE_STRONG);
		assertTrue(e.estimateStrength("123456") < QUITE_STRONG);
	}

	@Test
	public void testStrongPasswords() {
		PasswordStrengthEstimator e = new PasswordStrengthEstimatorImpl();
		// Industry standard
		assertTrue(e.estimateStrength("Tr0ub4dor&3") > QUITE_STRONG);
		assertTrue(e.estimateStrength("correcthorsebatterystaple")
				> QUITE_STRONG);
	}
}
