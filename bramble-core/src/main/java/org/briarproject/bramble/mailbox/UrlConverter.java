package org.briarproject.bramble.mailbox;

import org.briarproject.nullsafety.NotNullByDefault;

/**
 * An interface for converting an onion address to an HTTP URL, allowing the
 * conversion to be customised for integration tests.
 */
@NotNullByDefault
interface UrlConverter {

	/**
	 * Converts a raw onion address, excluding the .onion suffix, into an
	 * HTTP URL.
	 */
	String convertOnionToBaseUrl(String onion);
}
