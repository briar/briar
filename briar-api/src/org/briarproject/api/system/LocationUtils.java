package org.briarproject.api.system;

public interface LocationUtils {
	
	/** Get the country the device is currently-located in, or "" if it cannot
	 * be determined. Should never return {@code null}.
	 *
	 * <p>The country codes are formatted upper-case and as per <a href="
	 * https://en.wikipedia.org/wiki/ISO_3166-1_alpha-2">ISO 3166-1 alpha 2</a>.
	 */
	String getCurrentCountry();

}
