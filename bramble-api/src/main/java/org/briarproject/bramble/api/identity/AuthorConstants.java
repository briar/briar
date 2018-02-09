package org.briarproject.bramble.api.identity;

import static org.briarproject.bramble.api.crypto.CryptoConstants.MAX_SIGNATURE_BYTES;
import static org.briarproject.bramble.api.crypto.CryptoConstants.MAX_SIGNATURE_PUBLIC_KEY_BYTES;

public interface AuthorConstants {

	/**
	 * The maximum length of an author's name in UTF-8 bytes.
	 */
	int MAX_AUTHOR_NAME_LENGTH = 50;

	/**
	 * The maximum length of a public key in bytes. This applies to the
	 * signature algorithm used by the current {@link Author format version}.
	 */
	int MAX_PUBLIC_KEY_LENGTH = MAX_SIGNATURE_PUBLIC_KEY_BYTES;

	/**
	 * The maximum length of a signature in bytes. This applies to the
	 * signature algorithm used by the current {@link Author format version}.
	 */
	int MAX_SIGNATURE_LENGTH = MAX_SIGNATURE_BYTES;
}
