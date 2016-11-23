package org.briarproject.bramble.api.system;

import org.briarproject.bramble.api.nullsafety.NotNullByDefault;

@NotNullByDefault
public interface LocationUtils {

	/**
	 * Get the country the device is currently located in, or "" if it cannot
	 * be determined.
	 * <p>
	 * The country codes are formatted upper-case and as per <a href="
	 * https://en.wikipedia.org/wiki/ISO_3166-1_alpha-2">ISO 3166-1 alpha 2</a>.
	 */
	String getCurrentCountry();
}
