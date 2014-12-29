package org.briarproject.api.crypto;

public interface PasswordStrengthEstimator {

	float NONE = 0;
	float WEAK = 0.4f;
	float QUITE_WEAK = 0.6f;
	float QUITE_STRONG = 0.8f;
	float STRONG = 1;

	/**
	 * Returns an estimate between 0 (weakest) and 1 (strongest), inclusive,
	 * of the strength of the given password.
	 */
	float estimateStrength(String password);
}
