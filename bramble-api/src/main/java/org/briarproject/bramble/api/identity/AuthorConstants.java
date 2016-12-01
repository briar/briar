package org.briarproject.bramble.api.identity;

public interface AuthorConstants {

	/**
	 * The maximum length of an author's name in UTF-8 bytes.
	 */
	int MAX_AUTHOR_NAME_LENGTH = 50;

	/**
	 * The maximum length of a public key in bytes.
	 * <p>
	 * Public keys use SEC1 format: 0x04 x y, where x and y are unsigned
	 * big-endian integers.
	 * <p>
	 * For a 256-bit elliptic curve, the maximum length is 2 * 256 / 8 + 1.
	 */
	int MAX_PUBLIC_KEY_LENGTH = 65;

	/**
	 * The maximum length of a signature in bytes.
	 * <p>
	 * A signature is an ASN.1 DER sequence containing two integers, r and s.
	 * The format is 0x30 len1 0x02 len2 r 0x02 len3 s, where len1 is
	 * len(0x02 len2 r 0x02 len3 s) as a DER length, len2 is len(r) as a DER
	 * length, len3 is len(s) as a DER length, and r and s are signed
	 * big-endian integers of minimal length.
	 * <p>
	 * For a 256-bit elliptic curve, the lengths are one byte each, so the
	 * maximum length is 2 * 256 / 8 + 8.
	 */
	int MAX_SIGNATURE_LENGTH = 72;
}
