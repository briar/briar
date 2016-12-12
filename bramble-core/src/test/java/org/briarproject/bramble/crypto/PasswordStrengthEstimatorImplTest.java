package org.briarproject.bramble.crypto;

import org.briarproject.bramble.BrambleTestCase;
import org.briarproject.bramble.api.crypto.PasswordStrengthEstimator;
import org.junit.Test;

import static org.briarproject.bramble.api.crypto.PasswordStrengthEstimator.QUITE_STRONG;
import static org.junit.Assert.assertTrue;

public class PasswordStrengthEstimatorImplTest extends BrambleTestCase {

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
