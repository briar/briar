package org.briarproject.bramble.api.crypto;

import org.briarproject.bramble.api.nullsafety.NotNullByDefault;

@NotNullByDefault
public interface PasswordStrengthEstimator {

	float NONE = 0;
	float WEAK = 0.25f;
	float QUITE_WEAK = 0.5f;
	float QUITE_STRONG = 0.75f;
	float STRONG = 1;

	/**
	 * Returns an estimate between 0 (weakest) and 1 (strongest), inclusive,
	 * of the strength of the given password.
	 */
	float estimateStrength(String password);
}
