package org.briarproject.bramble.api.system;

import org.briarproject.bramble.api.nullsafety.NotNullByDefault;

import java.security.Provider;
import java.security.SecureRandom;

import javax.annotation.Nullable;

/**
 * Wrapper for a platform-specific secure random number generator.
 */
@NotNullByDefault
public interface SecureRandomProvider {

	/**
	 * Returns a {@link Provider} that provides a strong {@link SecureRandom}
	 * implementation, or null if the platform's default implementation should
	 * be used.
	 */
	@Nullable
	Provider getProvider();
}
